package org.vidhura.assessment.store;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks processed requestIds and transactionIds to prevent duplicate side effects.
 * In production this would be a durable store (DB unique constraint / Redis SET NX).
 */
@Component
public class IdempotencyStore {

    private final Map<String, String> requestIdToTransactionId = new ConcurrentHashMap<>();
    private final Map<String, String> transactionIdToRequestId = new ConcurrentHashMap<>();

    public boolean isRequestProcessed(String requestId) {
        return requestIdToTransactionId.containsKey(requestId);
    }

    public boolean isTransactionProcessed(String transactionId) {
        return transactionIdToRequestId.containsKey(transactionId);
    }

    public Optional<String> findRequestForTransaction(String transactionId) {
        return Optional.ofNullable(transactionIdToRequestId.get(transactionId));
    }

    /**
     * Atomically claims a request + business transaction for processing.
     * Returns false if either key was already claimed.
     */
    public synchronized boolean tryClaim(String requestId, String transactionId) {
        if (requestIdToTransactionId.containsKey(requestId)
                || transactionIdToRequestId.containsKey(transactionId)) {
            return false;
        }
        requestIdToTransactionId.put(requestId, transactionId);
        transactionIdToRequestId.put(transactionId, requestId);
        return true;
    }

    /**
     * Releases a claim after a failed attempt where no side effect was applied.
     * Safe retries can then re-claim the same keys.
     */
    public synchronized void release(String requestId, String transactionId) {
        requestIdToTransactionId.remove(requestId, transactionId);
        transactionIdToRequestId.remove(transactionId, requestId);
    }

    public void clear() {
        requestIdToTransactionId.clear();
        transactionIdToRequestId.clear();
    }
}
