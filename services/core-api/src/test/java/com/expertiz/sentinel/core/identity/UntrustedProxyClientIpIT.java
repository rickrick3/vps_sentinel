package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

/** Depuis un pair qui n'est pas un proxy de confiance, {@code X-Forwarded-For} est ignoré (spec §8.10). */
@TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=10\\.99\\.99\\.99")
class UntrustedProxyClientIpIT extends IntegrationTest {

    @Test
    void forwardedForIgnored() {
        String token = fixtures.accessToken(fixtures.createUser(RoleCode.VIEWER));

        http.get()
                .uri("/test/ip")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Forwarded-For", "10.1.2.3")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ip").value(ip -> assertThat(ip).isIn("127.0.0.1", "0:0:0:0:0:0:0:1"));
    }
}
