package com.expertiz.sentinel.core.shared.text;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IpAddressValidator implements ConstraintValidator<IpAddress, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String clean = TextRules.clean(value);
        return clean == null || IpAddresses.parse(clean).isPresent();
    }
}
