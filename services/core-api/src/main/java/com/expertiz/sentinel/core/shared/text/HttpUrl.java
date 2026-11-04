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

/** URL absolue {@code http} ou {@code https}, sans identifiants intégrés (spec §10.3, {@code repository_url}). */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface HttpUrl {

    String message() default "doit être une URL http ou https absolue, sans identifiants";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
