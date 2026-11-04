package com.expertiz.sentinel.core.shared.text;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.regex.Pattern;

public class HostnameValidator implements ConstraintValidator<Hostname, String> {

    private static final int MAX_LENGTH = 253;
    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    private boolean requireDot;

    @Override
    public void initialize(Hostname annotation) {
        requireDot = annotation.requireDot();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String hostname = TextRules.clean(value);
        if (hostname == null) {
            return true;
        }
        hostname = hostname.toLowerCase(Locale.ROOT);
        if (hostname.length() > MAX_LENGTH) {
            return false;
        }
        String[] labels = hostname.split("\\.", -1);
        if (requireDot && labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }
}
