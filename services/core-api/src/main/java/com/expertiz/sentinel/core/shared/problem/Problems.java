package com.expertiz.sentinel.core.shared.problem;

import com.expertiz.sentinel.core.shared.web.CorrelationIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Construction unique des Problem Details, pour l'advice MVC comme pour les filtres. */
final class Problems {

    private Problems() {}

    static ProblemDetail create(ProblemType type, String detail, List<FieldViolation> errors, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(type.status()), detail);
        problem.setType(type.type());
        problem.setTitle(type.title());
        problem.setInstance(instance(request));
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    /** Message générique d'un type de problème, quand aucun message plus précis n'existe. */
    static String genericDetail(ProblemType type) {
        return switch (type) {
            case VALIDATION_ERROR -> "Requête invalide.";
            case UNAUTHORIZED -> "Authentification requise.";
            case FORBIDDEN -> "Permission insuffisante.";
            case NOT_FOUND -> "Ressource introuvable.";
            case METHOD_NOT_ALLOWED -> "Méthode non autorisée pour cette ressource.";
            case NOT_ACCEPTABLE -> "Seul le format JSON est disponible.";
            case UNSUPPORTED_MEDIA_TYPE -> "Le contenu doit être au format JSON.";
            default -> "Une erreur interne est survenue.";
        };
    }

    // Lors d'un dispatch d'erreur, l'URI de la requête est /error : l'instance est l'URI d'origine.
    private static URI instance(HttpServletRequest request) {
        String uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String original
                ? original
                : request.getRequestURI();
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
