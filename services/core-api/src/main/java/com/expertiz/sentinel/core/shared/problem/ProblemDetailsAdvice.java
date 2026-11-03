package com.expertiz.sentinel.core.shared.problem;

import static java.util.stream.Collectors.toUnmodifiableMap;

import com.expertiz.sentinel.core.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Toutes les erreurs des contrôleurs en Problem Details (spec §11). Aucun message d'exception, valeur saisie ni
 * détail technique n'est renvoyé au client : seules les erreurs imprévues sont journalisées, avec le correlationId.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    private final Map<String, ConstraintMapping> constraints;

    public ProblemDetailsAdvice(List<ConstraintMapping> mappings) {
        this.constraints = mappings.stream().collect(toUnmodifiableMap(ConstraintMapping::constraint, m -> m));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> apiException(ApiException ex, HttpServletRequest request) {
        return respond(ex.type(), ex.getMessage(), ex.errors(), request, ex.headers());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> versionConflict(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(ProblemType.VERSION_CONFLICT, "Cet élément a été modifié par quelqu'un d'autre.", List.of(), request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        Optional<ConstraintMapping> mapping = constraintName(ex).map(constraints::get);
        if (mapping.isEmpty()) {
            return unexpected(ex, request);
        }
        ConstraintMapping m = mapping.get();
        return respond(m.type(), "Cette valeur est déjà utilisée.",
                List.of(new FieldViolation(m.field(), "valeur déjà utilisée")), request, null);
    }

    // Levée par la sécurité des méthodes : ce n'est pas une erreur interne.
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ProblemType.FORBIDDEN, "Permission insuffisante.", List.of(), request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Erreur inattendue (correlationId={})", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return respond(ProblemType.INTERNAL_ERROR, "Une erreur interne est survenue.", List.of(), request, null);
    }

    /** Erreurs propres à Spring MVC : corps illisible, validation, type de contenu, méthode, chemin inconnu… */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemType type = ProblemType.forFrameworkStatus(statusCode.value());
        if (type == ProblemType.INTERNAL_ERROR) {
            log.error("Erreur inattendue (correlationId={})", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        }
        List<FieldViolation> errors = ex instanceof MethodArgumentNotValidException invalid ? fieldErrors(invalid) : List.of();
        return respond(type, frameworkDetail(type, ex), errors, ((ServletWebRequest) request).getRequest(), headers);
    }

    private static String frameworkDetail(ProblemType type, Exception ex) {
        if (type != ProblemType.VALIDATION_ERROR) {
            return Problems.genericDetail(type);
        }
        return ex instanceof HttpMessageNotReadableException
                ? "Corps de requête invalide."
                : "Un ou plusieurs champs sont invalides.";
    }

    private static List<FieldViolation> fieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
    }

    private static Optional<String> constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException psql) {
                return Optional.ofNullable(psql.getServerErrorMessage()).map(ServerErrorMessage::getConstraint);
            }
        }
        return Optional.empty();
    }

    private static ResponseEntity<Object> respond(
            ProblemType type, String detail, List<FieldViolation> errors, HttpServletRequest request, HttpHeaders headers) {
        ProblemDetail problem = Problems.create(type, detail, errors, request);
        return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(type.status()));
    }
}
