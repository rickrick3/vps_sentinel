package com.expertiz.sentinel.core;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

/**
 * Profil {@code prod} (spec §8.11, §10) : springdoc y est désactivé, le contrat OpenAPI n'est pas servi. La chaîne de
 * sécurité laisse passer ces chemins sans jeton : ils répondent donc 404, pas 401. L'application démarre avec les
 * secrets fichiers et sert normalement ses endpoints.
 */
@ActiveProfiles("prod")
class ProdProfileIT extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml"})
    void apiDocsDisabled(String path) {
        http.get().uri(path).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:sentinel:problem:not-found");
    }

    @Test
    void applicationServesRequests() {
        http.get().uri("/actuator/health").exchange().expectStatus().isOk();
        http.get().uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(RoleCode.VIEWER)))
                .exchange()
                .expectStatus().isOk();
    }
}
