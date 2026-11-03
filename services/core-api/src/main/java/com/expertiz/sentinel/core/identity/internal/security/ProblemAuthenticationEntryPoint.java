package com.expertiz.sentinel.core.identity.internal.security;

import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.problem.ProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401 {@code unauthorized} en Problem Details (spec §8.11, §11), pour un jeton absent comme pour un jeton refusé. La
 * raison du refus (signature, expiration, claim) n'est jamais renvoyée : elle aiderait seulement un attaquant.
 */
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemWriter problems;

    ProblemAuthenticationEntryPoint(ProblemWriter problems) {
        this.problems = problems;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (exception instanceof OAuth2AuthenticationException) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            problems.write(request, response, ProblemType.UNAUTHORIZED, "Jeton d'accès invalide ou expiré.");
        } else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            problems.write(request, response, ProblemType.UNAUTHORIZED, "Authentification requise.");
        }
    }
}
