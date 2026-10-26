package org.vidhura.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntry(
        String transactionId,
        String requestId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant postedAt) {
}
