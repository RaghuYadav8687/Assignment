package org.vidhura.assessment.processing;

import org.springframework.stereotype.Component;
import org.vidhura.assessment.config.ProcessingProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forces a configurable number of transient failures for selected transactionIds
 * so retry behavior can be demonstrated without external dependencies.
 */
@Component
public class TransientFailureSimulator {

    private final ProcessingProperties properties;
    private final Map<String, AtomicInteger> attemptsByTransaction = new ConcurrentHashMap<>();

    public TransientFailureSimulator(ProcessingProperties properties) {
        this.properties = properties;
    }

    public void maybeFail(String transactionId) {
        int failTimes = properties.getSimulatedFailures().getOrDefault(transactionId, 0);
        if (failTimes <= 0) {
            return;
        }
        int attempt = attemptsByTransaction
                .computeIfAbsent(transactionId, id -> new AtomicInteger(0))
                .incrementAndGet();
        if (attempt <= failTimes) {
            throw new TransientFailureException(
                    "Simulated transient failure for " + transactionId + " on attempt " + attempt);
        }
    }

    public void reset() {
        attemptsByTransaction.clear();
    }
}
