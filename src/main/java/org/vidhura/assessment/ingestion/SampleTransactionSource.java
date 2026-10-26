package org.vidhura.assessment.ingestion;

import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.domain.TransactionType;

import java.math.BigDecimal;
import java.util.List;

/**
 * L2 suggested test data in arrival order (includes out-of-order, duplicate, invalid, retry).
 */
public final class SampleTransactionSource implements TransactionSource {

    @Override
    public List<TransactionRequest> load() {
        return List.of(
                req("TXN-2002", "REQ-2", "ACC-201", TransactionType.DEBIT, "40", 2),
                req("TXN-2001", "REQ-1", "ACC-201", TransactionType.CREDIT, "100", 1),
                req("TXN-2002", "REQ-2-DUP", "ACC-201", TransactionType.DEBIT, "40", 2),
                req("TXN-2003", "REQ-3", "ACC-201", TransactionType.CREDIT, "25", 3),
                req("TXN-2004", "REQ-4", "ACC-202", TransactionType.DEBIT, "-10", 4),
                req("TXN-2005", "REQ-5", "ACC-203", TransactionType.CREDIT, "500", 5));
    }

    private static TransactionRequest req(
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
}
