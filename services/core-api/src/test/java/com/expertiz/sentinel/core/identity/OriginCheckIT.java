package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Endpoints de session (spec §8.9) : {@code Origin} obligatoire et autorisé, vérifié avant tout le reste. Ils
 * n'acceptent pas le Bearer : seul le cookie de session y fait foi (M4).
 */
class OriginCheckIT extends IntegrationTest {

    private static final String[] SESSION_ENDPOINTS = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"};

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    void missingOriginIs403(String path) {
        http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:origin-not-allowed")
                .jsonPath("$.detail").isEqualTo("Origine non autorisée.")
                .jsonPath("$.instance").isEqualTo(path)
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example", "null", "http://localhost:5174", "https://localhost:5173", "http://localhost:5173/"})
    void foreignOriginIs403(String origin) {
        for (String path : SESSION_ENDPOINTS) {
            http.post()
                    .uri(path)
                    .header(HttpHeaders.ORIGIN, origin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.type").isEqualTo("urn:sentinel:problem:origin-not-allowed");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    void originIsCheckedBeforeTheContentType(String path) {
        http.post()
                .uri(path)
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .contentType(MediaType.TEXT_PLAIN)
                .body("email=a")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:origin-not-allowed");
    }

    /** Avec l'origine autorisée et sans jeton, la requête atteint l'endpoint (400 sans corps valide, 401 sans cookie, 204). */
    @ParameterizedTest
    @CsvSource({"/api/v1/auth/login, 400", "/api/v1/auth/refresh, 401", "/api/v1/auth/logout, 204"})
    void allowedOriginReachesTheEndpoint(String path, int status) {
        assertThat(postWithOrigin(path, null).getStatus().value()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    void sessionEndpointsIgnoreBearerTokens(String path) {
        // Un access token expiré ou invalide ne doit pas empêcher le logout ni le refresh (spec §8.5).
        EntityExchangeResult<String> without = postWithOrigin(path, null);
        EntityExchangeResult<String> with = postWithOrigin(path, "Bearer not-a-valid-token");

        assertThat(with.getStatus()).isEqualTo(without.getStatus());
        assertThat(with.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(String.valueOf(with.getResponseBody())).doesNotContain("Jeton d'accès invalide");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    void otherMethodsOnSessionPathsRequireAToken(String path) {
        http.get()
                .uri(path)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private EntityExchangeResult<String> postWithOrigin(String path, String authorization) {
        var request = http.post().uri(path).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.contentType(MediaType.APPLICATION_JSON).body("{}").exchange().expectBody(String.class).returnResult();
    }
}
