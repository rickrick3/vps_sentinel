package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** En-têtes de Spring Security conservés (spec §8.6, §8.11), aucune réponse CORS (spec §8.9), aucune session HTTP. */
class SecurityHeadersIT extends IntegrationTest {

    String token;

    @BeforeEach
    void token() {
        token = fixtures.accessToken(fixtures.createUser(RoleCode.VIEWER));
    }

    @Test
    void apiResponsesAreNoStore() {
        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.CACHE_CONTROL, v -> assertThat(v).contains("no-store"));
        http.get()
                .uri("/api/v1/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().value(HttpHeaders.CACHE_CONTROL, v -> assertThat(v).contains("no-store"));
    }

    @Test
    void contentSniffingAndFramingDisabled() {
        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    void noCorsHeaders() {
        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .exchange()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
    }

    @Test
    void corsPreflightIsNotAnswered() {
        for (String origin : new String[] {"https://evil.example", ALLOWED_ORIGIN}) {
            http.options()
                    .uri("/api/v1/me")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                    .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        }
    }

    @Test
    void noHttpSessionIsCreated() {
        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
        http.get()
                .uri("/api/v1/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
    }
}
