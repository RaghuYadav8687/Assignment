package org.vidhura.assessment.processing;

import org.vidhura.assessment.domain.TransactionRecord;

import java.util.Optional;
import java.util.TreeMap;

/**
 * Global sequence gap buffer (Option A: sequence-based out-of-order handling).
 * Sequences are stream-ordered across the input source, not per account.
 */
final class SequenceTracker {

    private long nextExpected = 1L;
    private final TreeMap<Long, TransactionRecord> pending = new TreeMap<>();

    long nextExpected() {
        return nextExpected;
    }

    void markProcessed(long sequenceNumber) {
        nextExpected = sequenceNumber + 1;
    }

    void buffer(TransactionRecord record) {
        pending.put(record.getRequest().getSequenceNumber(), record);
    }

    Optional<TransactionRecord> pollReady() {
        TransactionRecord ready = pending.remove(nextExpected);
        return Optional.ofNullable(ready);
    }

    void clear() {
        nextExpected = 1L;
        pending.clear();
    }
}
