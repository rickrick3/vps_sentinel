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

/** Refuse les caractères de contrôle Unicode ; {@code \n} et {@code \t} sont admis dans les descriptions (spec §10.3). */
@Documented
@Constraint(validatedBy = NoControlCharsValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface NoControlChars {

    String message() default "ne doit pas contenir de caractères de contrôle";

    boolean allowNewlines() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
