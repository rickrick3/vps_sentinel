package com.expertiz.sentinel.core.identity.internal.security;

import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.problem.ProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 403 {@code forbidden} en Problem Details pour un refus des filtres de Spring Security (spec §8.11). Les refus de
 * {@code @PreAuthorize} passent par {@code ProblemDetailsAdvice}, avec le même corps.
 */
class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemWriter problems;

    ProblemAccessDeniedHandler(ProblemWriter problems) {
        this.problems = problems;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        problems.write(request, response, ProblemType.FORBIDDEN, "Permission insuffisante.");
    }
}
