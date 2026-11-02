package com.expertiz.sentinel.core.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Application complète sur un port réel, connectée avec {@code sentinel_app} à la base partagée et migrée. Les
 * requêtes traversent Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

    protected static final SentinelPostgres DATABASE = SentinelPostgres.shared();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::appJdbcUrl);
        registry.add("spring.datasource.username", () -> SentinelPostgres.APP);
        registry.add("spring.datasource.password", () -> SentinelPostgres.APP_PASSWORD);
    }

    @LocalServerPort
    private int port;

    protected RestTestClient http;

    @BeforeEach
    void bindHttpClient() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    protected JdbcClient ownerJdbc() {
        return DATABASE.ownerJdbc();
    }

    protected JdbcClient appJdbc() {
        return DATABASE.appJdbc();
    }
}
