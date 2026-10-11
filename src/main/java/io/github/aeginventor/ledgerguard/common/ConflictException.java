package io.github.aeginventor.ledgerguard.common;

import io.github.aeginventor.ledgerguard.validation.ValidationResult;

public class ConflictException extends RuntimeException {

    private final transient ValidationResult validation;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, ValidationResult validation) {
        super(message);
        this.validation = validation;
    }

    public ValidationResult getValidation() {
        return validation;
    }
}
