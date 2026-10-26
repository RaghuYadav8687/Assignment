package org.vidhura.assessment.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.vidhura.assessment.config.ProcessingProperties;
import org.vidhura.assessment.domain.ProcessingSummary;
import org.vidhura.assessment.domain.TransactionRecord;
import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.domain.TransactionStatus;
import org.vidhura.assessment.store.AccountLedger;
import org.vidhura.assessment.store.IdempotencyStore;
import org.vidhura.assessment.store.TransactionStateStore;
import org.vidhura.assessment.validation.TransactionValidator;
import org.vidhura.assessment.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Background-style batch processor: validates, deduplicates, orders by sequence,
 * applies ledger effects idempotently, and retries transient failures.
 */
@Service
public class TransactionProcessingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingService.class);

    private final TransactionValidator validator;
    private final IdempotencyStore idempotencyStore;
    private final TransactionStateStore stateStore;
    private final AccountLedger accountLedger;
    private final TransactionBusinessProcessor businessProcessor;
    private final ProcessingProperties properties;
    private final TransientFailureSimulator failureSimulator;
    private final SequenceTracker sequenceTracker = new SequenceTracker();

    public TransactionProcessingService(
            TransactionValidator validator,
            IdempotencyStore idempotencyStore,
            TransactionStateStore stateStore,
            AccountLedger accountLedger,
            TransactionBusinessProcessor businessProcessor,
            ProcessingProperties properties,
            TransientFailureSimulator failureSimulator) {
        this.validator = validator;
        this.idempotencyStore = idempotencyStore;
        this.stateStore = stateStore;
        this.accountLedger = accountLedger;
        this.businessProcessor = businessProcessor;
        this.properties = properties;
        this.failureSimulator = failureSimulator;
    }

    public ProcessingSummary processBatch(List<TransactionRequest> requests) {
        log.info("event=batch_started count={}", requests.size());

        List<TransactionRecord> retryQueue = new ArrayList<>();

        for (TransactionRequest request : requests) {
            TransactionRecord record = ingest(request);
            if (record.getStatus() == TransactionStatus.RETRY_PENDING) {
                retryQueue.add(record);
            }
        }

        processRetryQueue(retryQueue);

        ProcessingSummary summary = new ProcessingSummary(
                stateStore.countsByStatus(),
                List.copyOf(stateStore.findAll()),
                accountLedger.snapshotBalances());

        log.info(
                "event=batch_completed processed={} duplicate={} pending={} failed={} retryPending={} nextExpectedSequence={}",
                summary.count(TransactionStatus.PROCESSED),
                summary.count(TransactionStatus.DUPLICATE),
                summary.count(TransactionStatus.PENDING),
                summary.count(TransactionStatus.FAILED),
                summary.count(TransactionStatus.RETRY_PENDING),
                sequenceTracker.nextExpected());

        return summary;
    }

    public void reset() {
        idempotencyStore.clear();
        stateStore.clear();
        accountLedger.clear();
        sequenceTracker.clear();
        failureSimulator.reset();
    }

    private TransactionRecord ingest(TransactionRequest request) {
        TransactionRecord record = new TransactionRecord(request);
        stateStore.save(record);

        ValidationResult validation = validator.validate(request);
        if (!validation.valid()) {
            record.mark(TransactionStatus.FAILED, validation.reason());
            // Consume sequence slot so invalid records do not stall the pipeline.
            consumeSequenceIfCurrent(request.getSequenceNumber());
            log.warn(
                    "event=validation_failed requestId={} transactionId={} reason={}",
                    request.getRequestId(),
                    request.getTransactionId(),
                    validation.reason());
            drainPending();
            return record;
        }

        if (isDuplicate(request)) {
            markDuplicate(record, "Duplicate request or transaction already processed");
            // If the duplicate occupies the current sequence slot, advance so the stream does not stall.
            consumeSequenceIfCurrent(request.getSequenceNumber());
            drainPending();
            return record;
        }

        return routeBySequence(record);
    }

    private void consumeSequenceIfCurrent(long sequenceNumber) {
        if (sequenceNumber == sequenceTracker.nextExpected()) {
            sequenceTracker.markProcessed(sequenceNumber);
        }
    }

    private boolean isDuplicate(TransactionRequest request) {
        return idempotencyStore.isRequestProcessed(request.getRequestId())
                || idempotencyStore.isTransactionProcessed(request.getTransactionId());
    }

    private void markDuplicate(TransactionRecord record, String reason) {
        record.mark(TransactionStatus.DUPLICATE, reason);
        log.info(
                "event=duplicate_detected requestId={} transactionId={}",
                record.getRequest().getRequestId(),
                record.getRequest().getTransactionId());
    }

    private TransactionRecord routeBySequence(TransactionRecord record) {
        TransactionRequest request = record.getRequest();
        long expected = sequenceTracker.nextExpected();
        long sequence = request.getSequenceNumber();

        if (sequence > expected) {
            record.mark(TransactionStatus.PENDING, "Waiting for sequence " + expected);
            sequenceTracker.buffer(record);
            log.info(
                    "event=out_of_order_buffered requestId={} transactionId={} sequence={} expected={}",
                    request.getRequestId(),
                    request.getTransactionId(),
                    sequence,
                    expected);
            return record;
        }

        if (sequence < expected) {
            markDuplicate(record, "Stale sequence; expected " + expected);
            return record;
        }

        return applyEffect(record);
    }

    private TransactionRecord applyEffect(TransactionRecord record) {
        TransactionRequest request = record.getRequest();
        record.mark(TransactionStatus.PROCESSING, "Applying ledger effect");
        record.incrementAttempt();

        if (!idempotencyStore.tryClaim(request.getRequestId(), request.getTransactionId())) {
            markDuplicate(record, "Idempotency keys already claimed");
            return record;
        }

        try {
            businessProcessor.apply(request);
            record.mark(TransactionStatus.PROCESSED, "Ledger posted successfully");
            sequenceTracker.markProcessed(request.getSequenceNumber());
            log.info(
                    "event=processed requestId={} transactionId={} attempt={}",
                    request.getRequestId(),
                    request.getTransactionId(),
                    record.getAttemptCount());
            drainPending();
            return record;
        } catch (TransientFailureException ex) {
            idempotencyStore.release(request.getRequestId(), request.getTransactionId());
            if (record.getAttemptCount() < properties.getMaxAttempts()) {
                record.mark(TransactionStatus.RETRY_PENDING, ex.getMessage());
                log.warn(
                        "event=retry_pending requestId={} transactionId={} attempt={} maxAttempts={} reason={}",
                        request.getRequestId(),
                        request.getTransactionId(),
                        record.getAttemptCount(),
                        properties.getMaxAttempts(),
                        ex.getMessage());
            } else {
                failAndUnblock(record, "Retry exhausted: " + ex.getMessage());
            }
            return record;
        } catch (RuntimeException ex) {
            idempotencyStore.release(request.getRequestId(), request.getTransactionId());
            failAndUnblock(record, "Unexpected error: " + ex.getMessage());
            log.error(
                    "event=processing_failed requestId={} transactionId={}",
                    request.getRequestId(),
                    request.getTransactionId(),
                    ex);
            return record;
        }
    }

    private void failAndUnblock(TransactionRecord record, String reason) {
        TransactionRequest request = record.getRequest();
        record.mark(TransactionStatus.FAILED, reason);
        sequenceTracker.markProcessed(request.getSequenceNumber());
        log.error(
                "event=failed requestId={} transactionId={} reason={}",
                request.getRequestId(),
                request.getTransactionId(),
                reason);
        drainPending();
    }

    private void drainPending() {
        while (true) {
            var ready = sequenceTracker.pollReady();
            if (ready.isEmpty()) {
                return;
            }
            TransactionRecord pending = ready.get();
            if (isDuplicate(pending.getRequest())) {
                markDuplicate(pending, "Duplicate after pending release");
                sequenceTracker.markProcessed(pending.getRequest().getSequenceNumber());
                continue;
            }
            TransactionRecord result = applyEffect(pending);
            if (result.getStatus() == TransactionStatus.RETRY_PENDING) {
                return;
            }
        }
    }

    private void processRetryQueue(List<TransactionRecord> retryQueue) {
        int guard = 0;
        int maxLoops = properties.getMaxAttempts() * Math.max(1, retryQueue.size() + 5);

        while (!retryQueue.isEmpty() && guard++ < maxLoops) {
            TransactionRecord record = retryQueue.removeFirst();
            if (record.getStatus() != TransactionStatus.RETRY_PENDING) {
                continue;
            }

            if (record.getRequest().getSequenceNumber() != sequenceTracker.nextExpected()) {
                retryQueue.add(record);
                continue;
            }

            TransactionRecord result = applyEffect(record);
            if (result.getStatus() == TransactionStatus.RETRY_PENDING) {
                retryQueue.add(result);
            }

            for (TransactionRecord candidate : stateStore.findByStatus(TransactionStatus.RETRY_PENDING)) {
                if (!retryQueue.contains(candidate)) {
                    retryQueue.add(candidate);
                }
            }
        }
    }
}
