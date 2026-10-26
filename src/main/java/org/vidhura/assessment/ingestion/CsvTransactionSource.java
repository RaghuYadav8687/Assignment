package org.vidhura.assessment.ingestion;

import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.domain.TransactionType;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV columns:
 * transactionId,requestId,accountId,transactionType,amount,currency,sequenceNumber
 */
public final class CsvTransactionSource implements TransactionSource {

    private final Path path;

    public CsvTransactionSource(Path path) {
        this.path = path;
    }

    @Override
    public List<TransactionRequest> load() {
        try {
            List<String> lines = Files.readAllLines(path);
            List<TransactionRequest> requests = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("transactionId")) {
                    continue;
                }
                requests.add(parseLine(trimmed));
            }
            return List.copyOf(requests);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read transaction CSV: " + path, ex);
        }
    }

    private static TransactionRequest parseLine(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length < 7) {
            throw new IllegalArgumentException("CSV line must have 7 columns: " + line);
        }
        return new TransactionRequest(
                parts[0].trim(),
                parts[1].trim(),
                parts[2].trim(),
                TransactionType.valueOf(parts[3].trim().toUpperCase()),
                new BigDecimal(parts[4].trim()),
                parts[5].trim(),
                Long.parseLong(parts[6].trim()));
    }
}
