package org.vidhura.assessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "assessment.processing")
public class ProcessingProperties {

    /**
     * Maximum attempts for a retryable transient failure (including the first try).
     */
    private int maxAttempts = 3;

    /**
     * transactionId -> how many consecutive attempts should fail before succeeding.
     */
    private Map<String, Integer> simulatedFailures = new HashMap<>();

    /**
     * Optional CSV path; when blank, built-in sample batch is used.
     */
    private String inputFile = "";

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Map<String, Integer> getSimulatedFailures() {
        return simulatedFailures;
    }

    public void setSimulatedFailures(Map<String, Integer> simulatedFailures) {
        this.simulatedFailures = simulatedFailures;
    }

    public String getInputFile() {
        return inputFile;
    }

    public void setInputFile(String inputFile) {
        this.inputFile = inputFile;
    }
}
