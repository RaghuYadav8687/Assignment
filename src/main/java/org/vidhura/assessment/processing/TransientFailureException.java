package org.vidhura.assessment.processing;

/**
 * Simulated transient infrastructure/business dependency failure.
 */
public class TransientFailureException extends RuntimeException {

    public TransientFailureException(String message) {
        super(message);
    }
}
