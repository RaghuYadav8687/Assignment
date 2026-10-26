package org.vidhura.assessment.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ProcessingSummary {

    private final Map<TransactionStatus, Long> countsByStatus;
    private final List<TransactionRecord> records;
    private final Map<String, java.math.BigDecimal> balancesByAccount;

    public ProcessingSummary(
            Map<TransactionStatus, Long> countsByStatus,
            List<TransactionRecord> records,
            Map<String, java.math.BigDecimal> balancesByAccount) {
        this.countsByStatus = Collections.unmodifiableMap(new EnumMap<>(countsByStatus));
        this.records = List.copyOf(records);
        this.balancesByAccount = Map.copyOf(balancesByAccount);
    }

    public Map<TransactionStatus, Long> getCountsByStatus() {
        return countsByStatus;
    }

    public List<TransactionRecord> getRecords() {
        return records;
    }

    public Map<String, java.math.BigDecimal> getBalancesByAccount() {
        return balancesByAccount;
    }

    public long count(TransactionStatus status) {
        return countsByStatus.getOrDefault(status, 0L);
    }
}
