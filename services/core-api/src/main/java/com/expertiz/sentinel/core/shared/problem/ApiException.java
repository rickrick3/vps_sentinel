package com.expertiz.sentinel.core.shared.problem;

import java.util.List;

/**
 * Erreur métier rendue en Problem Details. Le message est le {@code detail} renvoyé au client : il ne doit jamais
 * reprendre une valeur saisie sur un endpoint d'authentification (spec §11).
 */
public class ApiException extends RuntimeException {

    private final ProblemType type;
    private final List<FieldViolation> errors;

    public ApiException(ProblemType type, String detail) {
        this(type, detail, List.of());
    }

    public ApiException(ProblemType type, String detail, List<FieldViolation> errors) {
        super(detail);
        this.type = type;
        this.errors = List.copyOf(errors);
    }

    public ProblemType type() {
        return type;
    }

    public List<FieldViolation> errors() {
        return errors;
    }
}
