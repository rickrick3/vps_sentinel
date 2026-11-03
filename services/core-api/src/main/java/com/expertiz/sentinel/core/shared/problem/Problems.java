package com.expertiz.sentinel.core.shared.problem;

import com.expertiz.sentinel.core.shared.web.CorrelationIdFilter;
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

    private static URI instance(HttpServletRequest request) {
        try {
            return URI.create(request.getRequestURI());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
