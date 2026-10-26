package org.vidhura.assessment.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.vidhura.assessment.domain.ProcessingSummary;
import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.ingestion.TransactionSource;
import org.vidhura.assessment.ingestion.TransactionSourceFactory;
import org.vidhura.assessment.processing.TransactionProcessingService;
import org.vidhura.assessment.report.ProcessingReportLogger;

import java.util.List;

/**
 * Simulates a scheduled / queue-triggered background job on application startup.
 */
@Component
public class TransactionProcessingRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingRunner.class);

    private final TransactionSourceFactory sourceFactory;
    private final TransactionProcessingService processingService;
    private final ProcessingReportLogger reportLogger;

    public TransactionProcessingRunner(
            TransactionSourceFactory sourceFactory,
            TransactionProcessingService processingService,
            ProcessingReportLogger reportLogger) {
        this.sourceFactory = sourceFactory;
        this.processingService = processingService;
        this.reportLogger = reportLogger;
    }

    @Override
    public void run(ApplicationArguments args) {
        TransactionSource source = sourceFactory.create();
        List<TransactionRequest> requests = source.load();
        log.info("event=job_triggered source={} count={}", source.getClass().getSimpleName(), requests.size());
        ProcessingSummary summary = processingService.processBatch(requests);
        reportLogger.logSummary(summary);
    }
}
