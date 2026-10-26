package org.vidhura.assessment.domain;

/**
 * Lifecycle of a transaction request through background processing.
 */
public enum TransactionStatus {
    RECEIVED,
    PROCESSING,
    PENDING,
    PROCESSED,
    DUPLICATE,
    FAILED,
    RETRY_PENDING
}
