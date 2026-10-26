package org.vidhura.assessment.validation;

import org.springframework.stereotype.Component;
import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.domain.TransactionType;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class TransactionValidator {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "INR");

    public ValidationResult validate(TransactionRequest request) {
        if (request == null) {
            return ValidationResult.invalid("Request is null");
        }
        if (isBlank(request.getTransactionId())) {
            return ValidationResult.invalid("transactionId is required");
        }
        if (isBlank(request.getRequestId())) {
            return ValidationResult.invalid("requestId is required");
        }
        if (isBlank(request.getAccountId())) {
            return ValidationResult.invalid("accountId is required");
        }
        if (request.getTransactionType() == null) {
            return ValidationResult.invalid("transactionType is required");
        }
        if (request.getAmount() == null) {
            return ValidationResult.invalid("amount is required");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.invalid("amount must be greater than zero");
        }
        if (isBlank(request.getCurrency())) {
            return ValidationResult.invalid("currency is required");
        }
        if (!SUPPORTED_CURRENCIES.contains(request.getCurrency().toUpperCase())) {
            return ValidationResult.invalid("unsupported currency: " + request.getCurrency());
        }
        if (request.getSequenceNumber() <= 0) {
            return ValidationResult.invalid("sequenceNumber must be positive");
        }
        if (request.getTransactionType() == TransactionType.REVERSAL
                && request.getAmount().signum() <= 0) {
            return ValidationResult.invalid("REVERSAL amount must be positive");
        }
        return ValidationResult.ok();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
