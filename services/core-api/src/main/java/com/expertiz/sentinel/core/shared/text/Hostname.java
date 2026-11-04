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

/**
 * Nom d'hôte RFC 1123 (spec §10.3) : 253 caractères au plus, segments de 1 à 63 caractères {@code [a-z0-9-]} sans
 * tiret aux extrémités, insensible à la casse (la mise en minuscules se fait dans les services). Avec
 * {@code requireDot}, il faut au moins un point : c'est la règle des domaines. Pas de joker, pas de point final, noms
 * internationalisés en punycode uniquement.
 */
@Documented
@Constraint(validatedBy = HostnameValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface Hostname {

    String message() default "nom d'hôte invalide";

    boolean requireDot() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
