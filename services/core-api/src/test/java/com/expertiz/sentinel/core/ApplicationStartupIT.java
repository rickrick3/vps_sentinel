package com.expertiz.sentinel.core;

import com.expertiz.sentinel.core.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class ApplicationStartupIT extends IntegrationTest {

    @Test
    void healthIsUpWithoutDetails() {
        http.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP")
                .jsonPath("$.components")
                .doesNotExist();
    }
}
