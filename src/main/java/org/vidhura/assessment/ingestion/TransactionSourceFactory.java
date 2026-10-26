package org.vidhura.assessment.ingestion;

import org.springframework.stereotype.Component;
import org.vidhura.assessment.config.ProcessingProperties;

import java.nio.file.Path;

@Component
public class TransactionSourceFactory {

    private final ProcessingProperties properties;

    public TransactionSourceFactory(ProcessingProperties properties) {
        this.properties = properties;
    }

    public TransactionSource create() {
        String inputFile = properties.getInputFile();
        if (inputFile == null || inputFile.isBlank()) {
            return new SampleTransactionSource();
        }
        return new CsvTransactionSource(Path.of(inputFile));
    }
}
