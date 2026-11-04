package com.expertiz.sentinel.core.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.shared.problem.ProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.json.JsonMapper;

/** 401 et 403 de Spring Security en Problem Details (spec §8.11, §11), sans le message de l'exception. */
class SecurityProblemHandlersTest {

    private final ProblemWriter problems = new ProblemWriter(JsonMapper.builder().build());
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/servers");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void missingCredentialsIs401() throws Exception {
        new ProblemAuthenticationEntryPoint(problems)
                .commence(request, response, new InsufficientAuthenticationException("Full authentication is required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"urn:sentinel:problem:unauthorized\"")
                .contains("\"detail\":\"Authentification requise.\"")
                .contains("\"instance\":\"/api/v1/servers\"")
                .doesNotContain("Full authentication");
    }

    @Test
    void invalidTokenIs401WithoutTheReason() throws Exception {
        new ProblemAuthenticationEntryPoint(problems)
                .commence(request, response, new InvalidBearerTokenException("Jwt expired at 2026-11-03T10:15:00Z"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
        assertThat(response.getContentAsString())
                .contains("\"detail\":\"Jeton d'accès invalide ou expiré.\"")
                .doesNotContain("expired")
                .doesNotContain("2026-11-03");
    }

    @Test
    void accessDeniedIs403() throws Exception {
        new ProblemAccessDeniedHandler(problems).handle(request, response, new AccessDeniedException("Access Denied: internal"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"urn:sentinel:problem:forbidden\"")
                .contains("\"detail\":\"Permission insuffisante.\"")
                .doesNotContain("internal");
    }
}
