package org.vidhura.assessment.store;

import org.springframework.stereotype.Component;
import org.vidhura.assessment.domain.TransactionRecord;
import org.vidhura.assessment.domain.TransactionStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TransactionStateStore {

    private final Map<String, TransactionRecord> byRequestId = new ConcurrentHashMap<>();

    public TransactionRecord save(TransactionRecord record) {
        byRequestId.put(record.getRequest().getRequestId(), record);
        return record;
    }

    public Optional<TransactionRecord> findByRequestId(String requestId) {
        return Optional.ofNullable(byRequestId.get(requestId));
    }

    public Collection<TransactionRecord> findAll() {
        return List.copyOf(byRequestId.values());
    }

    public Map<TransactionStatus, Long> countsByStatus() {
        Map<TransactionStatus, Long> counts = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus status : TransactionStatus.values()) {
            counts.put(status, 0L);
        }
        for (TransactionRecord record : byRequestId.values()) {
            counts.merge(record.getStatus(), 1L, Long::sum);
        }
        return counts;
    }

    public List<TransactionRecord> findByStatus(TransactionStatus status) {
        List<TransactionRecord> result = new ArrayList<>();
        for (TransactionRecord record : byRequestId.values()) {
            if (record.getStatus() == status) {
                result.add(record);
            }
        }
        return result;
    }

    public void clear() {
        byRequestId.clear();
    }
}
