package org.vidhura.assessment.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable inbound transaction request. {@code requestId} is the idempotency key;
 * {@code transactionId} is the business transaction identifier.
 */
public final class TransactionRequest {

    private final String transactionId;
    private final String requestId;
    private final String accountId;
    private final TransactionType transactionType;
    private final BigDecimal amount;
    private final String currency;
    private final long sequenceNumber;

    public TransactionRequest(
            String transactionId,
            String requestId,
            String accountId,
            TransactionType transactionType,
            BigDecimal amount,
            String currency,
            long sequenceNumber) {
        this.transactionId = transactionId;
        this.requestId = requestId;
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.sequenceNumber = sequenceNumber;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionRequest that)) {
            return false;
        }
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public String toString() {
        return "TransactionRequest{transactionId='%s', requestId='%s', accountId='%s', type=%s, amount=%s, seq=%d}"
                .formatted(transactionId, requestId, accountId, transactionType, amount, sequenceNumber);
    }
}
