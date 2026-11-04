package com.expertiz.sentinel.core.shared.problem;

import java.util.List;
import org.springframework.http.HttpHeaders;

/**
 * Erreur métier rendue en Problem Details. Le message est le {@code detail} renvoyé au client : il ne doit jamais
 * reprendre une valeur saisie sur un endpoint d'authentification (spec §11). Des en-têtes peuvent accompagner la
 * réponse ({@code Retry-After}, effacement d'un cookie).
 */
public class ApiException extends RuntimeException {

    private final ProblemType type;
    private final List<FieldViolation> errors;
    private final HttpHeaders headers;

    public ApiException(ProblemType type, String detail) {
        this(type, detail, List.of());
    }

    public ApiException(ProblemType type, String detail, List<FieldViolation> errors) {
        this(type, detail, errors, HttpHeaders.EMPTY);
    }

    public ApiException(ProblemType type, String detail, List<FieldViolation> errors, HttpHeaders headers) {
        super(detail);
        this.type = type;
        this.errors = List.copyOf(errors);
        this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
    }

    public ProblemType type() {
        return type;
    }

    public List<FieldViolation> errors() {
        return errors;
    }

    public HttpHeaders headers() {
        return headers;
    }
}
