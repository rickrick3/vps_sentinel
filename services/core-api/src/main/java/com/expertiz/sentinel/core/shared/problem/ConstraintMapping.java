package com.expertiz.sentinel.core.shared.problem;

/**
 * Traduction d'une contrainte PostgreSQL nommée en erreur de champ (spec §9.1). Chaque module déclare ses
 * correspondances en beans ; une violation de contrainte non déclarée reste une erreur interne.
 */
public record ConstraintMapping(String constraint, ProblemType type, String field) {}
