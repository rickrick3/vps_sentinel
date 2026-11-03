package com.expertiz.sentinel.core.shared.problem;

/** Erreur rattachée à un champ de la requête, rendue dans {@code errors[]}. */
public record FieldViolation(String field, String message) {}
