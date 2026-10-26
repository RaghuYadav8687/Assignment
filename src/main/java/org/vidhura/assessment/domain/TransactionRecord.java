package org.vidhura.assessment.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Mutable processing state for a single request. Side-effect payload stays on the request;
 * this record tracks lifecycle and operational metadata only.
 */
public class TransactionRecord {

    private final TransactionRequest request;
    private TransactionStatus status;
    private String reason;
    private int attemptCount;
    private Instant updatedAt;

    public TransactionRecord(TransactionRequest request) {
        this.request = Objects.requireNonNull(request, "request");
        this.status = TransactionStatus.RECEIVED;
        this.attemptCount = 0;
        this.updatedAt = Instant.now();
    }

    public TransactionRequest getRequest() {
        return request;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void mark(TransactionStatus status, String reason) {
        this.status = status;
        this.reason = reason;
        this.updatedAt = Instant.now();
    }

    public void incrementAttempt() {
        this.attemptCount++;
        this.updatedAt = Instant.now();
    }
}
