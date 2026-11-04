package com.expertiz.sentinel.core.shared.text;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoControlCharsValidator implements ConstraintValidator<NoControlChars, String> {

    private boolean allowNewlines;

    @Override
    public void initialize(NoControlChars annotation) {
        allowNewlines = annotation.allowNewlines();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.codePoints().noneMatch(this::forbidden);
    }

    private boolean forbidden(int codePoint) {
        boolean newline = codePoint == '\n' || codePoint == '\t';
        return Character.getType(codePoint) == Character.CONTROL && !(allowNewlines && newline);
    }
}
