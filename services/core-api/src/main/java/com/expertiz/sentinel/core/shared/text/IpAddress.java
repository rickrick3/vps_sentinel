package com.expertiz.sentinel.core.shared.text;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Adresse IPv4 ou IPv6 littérale (spec §10.3) ; une valeur absente est admise. */
@Documented
@Constraint(validatedBy = IpAddressValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface IpAddress {

    String message() default "adresse IP invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
