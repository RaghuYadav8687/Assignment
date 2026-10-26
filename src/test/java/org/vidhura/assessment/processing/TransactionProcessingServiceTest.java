package org.vidhura.assessment.processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vidhura.assessment.config.ProcessingProperties;
import org.vidhura.assessment.domain.ProcessingSummary;
import org.vidhura.assessment.domain.TransactionRecord;
import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.domain.TransactionStatus;
import org.vidhura.assessment.domain.TransactionType;
import org.vidhura.assessment.ingestion.SampleTransactionSource;
import org.vidhura.assessment.store.AccountLedger;
import org.vidhura.assessment.store.IdempotencyStore;
import org.vidhura.assessment.store.TransactionStateStore;
import org.vidhura.assessment.validation.TransactionValidator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionProcessingServiceTest {

    private TransactionProcessingService service;
    private AccountLedger ledger;
    private ProcessingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ProcessingProperties();
        properties.setMaxAttempts(3);
        properties.getSimulatedFailures().put("TXN-2005", 1);

        ledger = new AccountLedger();
        IdempotencyStore idempotencyStore = new IdempotencyStore();
        TransactionStateStore stateStore = new TransactionStateStore();
        TransientFailureSimulator simulator = new TransientFailureSimulator(properties);
        TransactionBusinessProcessor businessProcessor =
                new TransactionBusinessProcessor(ledger, simulator);

        service = new TransactionProcessingService(
                new TransactionValidator(),
                idempotencyStore,
                stateStore,
                ledger,
                businessProcessor,
                properties,
                simulator);
    }

    @Test
    void l2SampleBatch_handlesOutOfOrderDuplicateInvalidAndRetry() {
        ProcessingSummary summary = service.processBatch(new SampleTransactionSource().load());
        Map<String, TransactionRecord> byRequest = indexByRequestId(summary);

        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-1").getStatus());
        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-2").getStatus());
        assertEquals(TransactionStatus.DUPLICATE, byRequest.get("REQ-2-DUP").getStatus());
        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-3").getStatus());
        assertEquals(TransactionStatus.FAILED, byRequest.get("REQ-4").getStatus());
        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-5").getStatus());
        assertTrue(byRequest.get("REQ-5").getAttemptCount() >= 2);

        assertEquals(0, new BigDecimal("85").compareTo(ledger.getBalance("ACC-201")));
        assertEquals(0, new BigDecimal("500").compareTo(ledger.getBalance("ACC-203")));
        assertEquals(1, ledger.countEntriesForTransaction("TXN-2002"));
        assertEquals(1, ledger.countEntriesForTransaction("TXN-2005"));
    }

    @Test
    void duplicateRequestId_doesNotDoublePost() {
        TransactionRequest first = request("TXN-1", "REQ-A", "ACC-1", TransactionType.CREDIT, "10", 1);
        TransactionRequest duplicate = request("TXN-1", "REQ-A", "ACC-1", TransactionType.CREDIT, "10", 1);

        service.processBatch(List.of(first));
        service.processBatch(List.of(duplicate));

        assertEquals(1, ledger.countEntriesForTransaction("TXN-1"));
        assertEquals(0, new BigDecimal("10").compareTo(ledger.getBalance("ACC-1")));
    }

    @Test
    void duplicateTransactionId_differentRequestId_isDuplicate() {
        List<TransactionRequest> batch = List.of(
                request("TXN-9", "REQ-1", "ACC-1", TransactionType.CREDIT, "10", 1),
                request("TXN-9", "REQ-2", "ACC-1", TransactionType.CREDIT, "10", 2));

        ProcessingSummary summary = service.processBatch(batch);
        Map<String, TransactionRecord> byRequest = indexByRequestId(summary);

        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-1").getStatus());
        assertEquals(TransactionStatus.DUPLICATE, byRequest.get("REQ-2").getStatus());
        assertEquals(1, ledger.countEntriesForTransaction("TXN-9"));
    }

    @Test
    void outOfOrder_buffersUntilGapFilled() {
        List<TransactionRequest> batch = List.of(
                request("TXN-2", "REQ-2", "ACC-1", TransactionType.DEBIT, "5", 2),
                request("TXN-1", "REQ-1", "ACC-1", TransactionType.CREDIT, "20", 1));

        ProcessingSummary summary = service.processBatch(batch);
        Map<String, TransactionRecord> byRequest = indexByRequestId(summary);

        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-1").getStatus());
        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-2").getStatus());
        assertEquals(0, new BigDecimal("15").compareTo(ledger.getBalance("ACC-1")));
    }

    @Test
    void invalidAmount_isFailedAndDoesNotStopBatch() {
        List<TransactionRequest> batch = List.of(
                request("TXN-1", "REQ-1", "ACC-1", TransactionType.CREDIT, "10", 1),
                request("TXN-2", "REQ-2", "ACC-1", TransactionType.DEBIT, "-1", 2),
                request("TXN-3", "REQ-3", "ACC-1", TransactionType.CREDIT, "5", 3));

        ProcessingSummary summary = service.processBatch(batch);
        Map<String, TransactionRecord> byRequest = indexByRequestId(summary);

        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-1").getStatus());
        assertEquals(TransactionStatus.FAILED, byRequest.get("REQ-2").getStatus());
        assertEquals(TransactionStatus.PROCESSED, byRequest.get("REQ-3").getStatus());
        assertEquals(0, new BigDecimal("15").compareTo(ledger.getBalance("ACC-1")));
    }

    @Test
    void transientFailure_retriesWithoutDuplicateSideEffect() {
        properties.getSimulatedFailures().put("TXN-RETRY", 1);

        ProcessingSummary summary = service.processBatch(List.of(
                request("TXN-RETRY", "REQ-R", "ACC-9", TransactionType.CREDIT, "50", 1)));

        TransactionRecord record = indexByRequestId(summary).get("REQ-R");
        assertEquals(TransactionStatus.PROCESSED, record.getStatus());
        assertEquals(2, record.getAttemptCount());
        assertEquals(1, ledger.countEntriesForTransaction("TXN-RETRY"));
    }

    @Test
    void retryExhaustion_marksFailed() {
        properties.setMaxAttempts(2);
        properties.getSimulatedFailures().put("TXN-POISON", 5);

        ProcessingSummary summary = service.processBatch(List.of(
                request("TXN-POISON", "REQ-P", "ACC-9", TransactionType.CREDIT, "50", 1)));

        TransactionRecord record = indexByRequestId(summary).get("REQ-P");
        assertEquals(TransactionStatus.FAILED, record.getStatus());
        assertEquals(2, record.getAttemptCount());
        assertEquals(0, ledger.countEntriesForTransaction("TXN-POISON"));
    }

    private static TransactionRequest request(
            String transactionId,
            String requestId,
            String accountId,
            TransactionType type,
            String amount,
            long sequence) {
        return new TransactionRequest(
                transactionId,
                requestId,
                accountId,
                type,
                new BigDecimal(amount),
                "USD",
                sequence);
    }

    private static Map<String, TransactionRecord> indexByRequestId(ProcessingSummary summary) {
        return summary.getRecords().stream()
                .collect(Collectors.toMap(r -> r.getRequest().getRequestId(), Function.identity()));
    }
}
