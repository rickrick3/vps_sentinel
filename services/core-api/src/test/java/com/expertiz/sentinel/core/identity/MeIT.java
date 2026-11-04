package com.expertiz.sentinel.core.identity;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** {@code GET /api/v1/me} (spec §10.1) : profil et permissions du compte, calculés depuis la base. */
class MeIT extends IntegrationTest {

    @Test
    void meReturnsProfile() {
        TestUser user = fixtures.createUser(RoleCode.DEVOPS);

        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(fixtures.accessToken(user)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo(user.id().toString())
                .jsonPath("$.email").isEqualTo(user.email())
                .jsonPath("$.fullName").isEqualTo("Utilisateur de test")
                .jsonPath("$.role").isEqualTo("DEVOPS")
                .jsonPath("$.permissions")
                .isEqualTo(List.of("project:delete", "project:read", "project:write", "server:read", "server:write"))
                .jsonPath("$.mustChangePassword").isEqualTo(false);
    }

    @Test
    void meWithoutTokenIs401Problem() {
        http.get()
                .uri("/api/v1/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:unauthorized")
                .jsonPath("$.title").isEqualTo("Authentification requise")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").isEqualTo("Authentification requise.")
                .jsonPath("$.instance").isEqualTo("/api/v1/me")
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @Test
    void correlationIdOfTheProblemIsTheRequestId() {
        http.get()
                .uri("/api/v1/me")
                .header("X-Request-Id", "req-me-401")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Request-Id", "req-me-401")
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo("req-me-401");
    }

    @Test
    void deletedAccountIs401EvenWithAValidToken() {
        TestUser user = fixtures.createUser(RoleCode.SUPER_ADMIN);
        String token = fixtures.accessToken(user);
        appJdbc().sql("UPDATE users SET deleted_at = now(), status = 'DISABLED' WHERE id = :id").param("id", user.id()).update();

        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:unauthorized");
    }

    @Test
    void disabledAccountIs401EvenWithAValidToken() {
        TestUser user = fixtures.createUser(RoleCode.SUPER_ADMIN);
        String token = fixtures.accessToken(user);
        appJdbc().sql("UPDATE users SET status = 'DISABLED' WHERE id = :id").param("id", user.id()).update();

        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:unauthorized");
    }
}
