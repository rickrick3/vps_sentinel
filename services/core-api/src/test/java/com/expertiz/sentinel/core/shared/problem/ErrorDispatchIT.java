package com.expertiz.sentinel.core.shared.problem;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Erreurs produites hors de Spring MVC, rendues par le dispatch d'erreur du conteneur (spec §11) : Problem Details avec
 * le statut d'origine, jamais converties en 401 pour un client anonyme. {@code /error} n'est pas une route publique.
 */
class ErrorDispatchIT extends IntegrationTest {

    @Test
    void firewallRejectionIsA400Problem() {
        // Le pare-feu de Spring Security refuse « ; » dans l'URL, avant toute authentification.
        http.get()
                .uri("/api/v1/me;jsessionid=abc")
                .header("X-Request-Id", "req-firewall")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals("X-Request-Id", "req-firewall")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:validation-error")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").isEqualTo("Requête invalide.")
                .jsonPath("$.instance").isEqualTo("/api/v1/me;jsessionid=abc")
                .jsonPath("$.correlationId").isEqualTo("req-firewall");
    }

    @Test
    void firewallRejectionWithATokenIsTheSameProblem() {
        String token = fixtures.accessToken(fixtures.createUser(RoleCode.VIEWER));

        http.get()
                .uri("/api/v1/me;jsessionid=abc")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:validation-error");
    }

    @Test
    void errorPathIsNotPublic() {
        http.get()
                .uri("/error")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:unauthorized");
    }

    @Test
    void errorPathRequestedDirectlyIsNotFound() {
        String token = fixtures.accessToken(fixtures.createUser(RoleCode.VIEWER));

        http.get()
                .uri("/error")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:not-found")
                .jsonPath("$.instance").isEqualTo("/error");
    }
}
