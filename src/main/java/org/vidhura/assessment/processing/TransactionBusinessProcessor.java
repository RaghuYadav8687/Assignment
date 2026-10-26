package org.vidhura.assessment.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.vidhura.assessment.domain.LedgerEntry;
import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.store.AccountLedger;

/**
 * Pure business side-effect application: post to the account ledger.
 * Separated from ingestion, ordering, and retry orchestration.
 */
@Component
public class TransactionBusinessProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionBusinessProcessor.class);

    private final AccountLedger accountLedger;
    private final TransientFailureSimulator failureSimulator;

    public TransactionBusinessProcessor(
            AccountLedger accountLedger,
            TransientFailureSimulator failureSimulator) {
        this.accountLedger = accountLedger;
        this.failureSimulator = failureSimulator;
    }

    public LedgerEntry apply(TransactionRequest request) {
        failureSimulator.maybeFail(request.getTransactionId());
        LedgerEntry entry = accountLedger.post(request);
        log.info(
                "event=ledger_posted transactionId={} requestId={} accountId={} type={} amount={} currency={}",
                request.getTransactionId(),
                request.getRequestId(),
                request.getAccountId(),
                request.getTransactionType(),
                request.getAmount(),
                request.getCurrency());
        return entry;
    }
}
