package org.vidhura.assessment.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.vidhura.assessment.domain.ProcessingSummary;
import org.vidhura.assessment.domain.TransactionRecord;
import org.vidhura.assessment.domain.TransactionStatus;

@Component
public class ProcessingReportLogger {

    private static final Logger log = LoggerFactory.getLogger(ProcessingReportLogger.class);

    public void logSummary(ProcessingSummary summary) {
        log.info("======== Processing Summary ========");
        for (TransactionStatus status : TransactionStatus.values()) {
            long count = summary.count(status);
            if (count > 0) {
                log.info("status={} count={}", status, count);
            }
        }
        summary.getBalancesByAccount().forEach(
                (accountId, balance) -> log.info("account={} balance={}", accountId, balance));
        log.info("-------- Record Details --------");
        for (TransactionRecord record : summary.getRecords()) {
            log.info(
                    "requestId={} transactionId={} status={} attempts={} reason={}",
                    record.getRequest().getRequestId(),
                    record.getRequest().getTransactionId(),
                    record.getStatus(),
                    record.getAttemptCount(),
                    record.getReason());
        }
        log.info("===================================");
    }
}
