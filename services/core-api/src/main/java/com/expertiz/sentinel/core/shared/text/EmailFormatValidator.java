package com.expertiz.sentinel.core.shared.text;

import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EmailFormatValidator implements ConstraintValidator<EmailFormat, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || EmailAddress.isValid(Fingerprints.normalizeIdentifier(value));
    }
}
