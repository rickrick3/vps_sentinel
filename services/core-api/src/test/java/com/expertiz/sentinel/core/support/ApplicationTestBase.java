package com.expertiz.sentinel.core.support;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Application complète sur un port réel, connectée avec {@code sentinel_app} à une base migrée. Les requêtes traversent
 * Tomcat. La base est fournie par la sous-classe : partagée ({@link IntegrationTest}) ou propre à la classe
 * ({@link IsolatedDatabaseIT}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestClockConfig.class, Fixtures.class, TestEndpoints.class})
public abstract class ApplicationTestBase {

    /** Seule origine autorisée pendant les tests (celle du frontend de dev). */
    public static final String ALLOWED_ORIGIN = "http://localhost:5173";

    /** Propriétés communes : base, vrai chemin de lecture des secrets (clés générées une fois par JVM), origine. */
    protected static void connect(DynamicPropertyRegistry registry, SentinelPostgres database) {
        registry.add("spring.datasource.url", database::appJdbcUrl);
        registry.add("spring.datasource.username", () -> SentinelPostgres.APP);
        registry.add("spring.datasource.password", () -> SentinelPostgres.APP_PASSWORD);
        registry.add("sentinel.secrets.dir", () -> TestSecrets.directory().toString());
        registry.add("sentinel.security.allowed-origins", () -> ALLOWED_ORIGIN);
    }

    @LocalServerPort
    private int port;

    protected RestTestClient http;

    /** Endpoints de session, appelés comme par le frontend. */
    protected AuthClient auth;

    /** Horloge de l'application, remise à l'heure courante avant chaque test. */
    @Autowired
    protected TestClock clock;

    @Autowired
    protected Fixtures fixtures;

    /** Base de l'application sous test. */
    protected abstract SentinelPostgres database();

    @BeforeEach
    void bindHttpClient() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        auth = new AuthClient(http);
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Requête authentifiée, corps JSON facultatif. */
    protected Response api(String token, HttpMethod method, String uri, Object body) {
        var request = http.method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        EntityExchangeResult<String> result = request.exchange().expectBody(String.class).returnResult();
        return new Response(result.getStatus().value(), result.getResponseHeaders(), result.getResponseBody());
    }

    /** Jeton d'un nouvel utilisateur actif du rôle donné. */
    protected String tokenFor(RoleCode role) {
        return fixtures.accessToken(fixtures.createUser(role));
    }

    protected JdbcClient ownerJdbc() {
        return database().ownerJdbc();
    }

    protected JdbcClient appJdbc() {
        return database().appJdbc();
    }
}
