package com.expertiz.sentinel.core.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import({TestClockConfig.class, Fixtures.class, TestEndpoints.class})
public abstract class IntegrationTest {

    protected static final SentinelPostgres DATABASE = SentinelPostgres.shared();

    /** Seule origine autorisée pendant les tests (celle du frontend de dev). */
    public static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::appJdbcUrl);
        registry.add("spring.datasource.username", () -> SentinelPostgres.APP);
        registry.add("spring.datasource.password", () -> SentinelPostgres.APP_PASSWORD);
        // Vrai chemin de lecture des secrets, avec des clés générées une fois par JVM.
        registry.add("sentinel.secrets.dir", () -> TestSecrets.directory().toString());
        registry.add("sentinel.security.allowed-origins", () -> ALLOWED_ORIGIN);
    }

    @LocalServerPort
    private int port;

    protected RestTestClient http;

    /** Horloge de l'application, remise à l'heure courante avant chaque test. */
    @Autowired
    protected TestClock clock;

    @Autowired
    protected Fixtures fixtures;

    @BeforeEach
    void bindHttpClient() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected JdbcClient ownerJdbc() {
        return DATABASE.ownerJdbc();
    }

    protected JdbcClient appJdbc() {
        return DATABASE.appJdbc();
    }
}
