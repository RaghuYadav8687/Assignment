package org.vidhura.assessment.store;

import org.springframework.stereotype.Component;
import org.vidhura.assessment.domain.LedgerEntry;
import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory account balances and ledger. Posting is the only business side effect.
 */
@Component
public class AccountLedger {

    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final List<LedgerEntry> entries = new ArrayList<>();

    public synchronized LedgerEntry post(TransactionRequest request) {
        BigDecimal delta = signedAmount(request);
        balances.merge(request.getAccountId(), delta, BigDecimal::add);

        LedgerEntry entry = new LedgerEntry(
                request.getTransactionId(),
                request.getRequestId(),
                request.getAccountId(),
                request.getTransactionType(),
                request.getAmount(),
                request.getCurrency(),
                Instant.now());
        entries.add(entry);
        return entry;
    }

    public BigDecimal getBalance(String accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> snapshotBalances() {
        return Map.copyOf(balances);
    }

    public List<LedgerEntry> getEntries() {
        return List.copyOf(entries);
    }

    public long countEntriesForTransaction(String transactionId) {
        return entries.stream().filter(e -> e.transactionId().equals(transactionId)).count();
    }

    public void clear() {
        balances.clear();
        synchronized (this) {
            entries.clear();
        }
    }

    private static BigDecimal signedAmount(TransactionRequest request) {
        return switch (request.getTransactionType()) {
            case CREDIT -> request.getAmount();
            case DEBIT, REVERSAL -> request.getAmount().negate();
        };
    }
}
