package com.expertiz.sentinel.core.identity;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** IP réelle du client (spec §8.10) : {@code X-Forwarded-For} n'est cru que venant d'un proxy de confiance (127.0.0.1). */
class ClientIpIT extends IntegrationTest {

    String token;

    @BeforeEach
    void token() {
        token = fixtures.accessToken(fixtures.createUser(RoleCode.VIEWER));
    }

    @Test
    void trustedProxyForwardedForIsUsed() {
        http.get()
                .uri("/test/ip")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Forwarded-For", "10.1.2.3")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ip").isEqualTo("10.1.2.3");
    }

    @Test
    void ipv6ForwardedForIsCanonical() {
        http.get()
                .uri("/test/ip")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Forwarded-For", "2001:db8::1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ip").isEqualTo("2001:db8:0:0:0:0:0:1");
    }
}
