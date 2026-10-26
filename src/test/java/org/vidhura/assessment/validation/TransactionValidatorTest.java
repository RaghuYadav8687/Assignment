package org.vidhura.assessment.validation;

import org.junit.jupiter.api.Test;
import org.vidhura.assessment.domain.TransactionRequest;
import org.vidhura.assessment.domain.TransactionType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionValidatorTest {

    private final TransactionValidator validator = new TransactionValidator();

    @Test
    void acceptsValidRequest() {
        ValidationResult result = validator.validate(new TransactionRequest(
                "TXN-1", "REQ-1", "ACC-1", TransactionType.CREDIT, new BigDecimal("10"), "USD", 1));
        assertTrue(result.valid());
    }

    @Test
    void rejectsNonPositiveAmount() {
        ValidationResult result = validator.validate(new TransactionRequest(
                "TXN-1", "REQ-1", "ACC-1", TransactionType.DEBIT, new BigDecimal("-10"), "USD", 1));
        assertFalse(result.valid());
    }

    @Test
    void rejectsMissingRequestId() {
        ValidationResult result = validator.validate(new TransactionRequest(
                "TXN-1", " ", "ACC-1", TransactionType.CREDIT, new BigDecimal("10"), "USD", 1));
        assertFalse(result.valid());
    }
}
