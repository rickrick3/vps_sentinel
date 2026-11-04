package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.security.Permissions;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Matrice d'autorisation (spec §13.4) : une entrée par ligne du spec §10.1, croisée avec chaque rôle. Les requêtes
 * portent des ids aléatoires et un corps minimal : un rôle autorisé obtient une réponse métier (200, 400, 404…), jamais
 * 401 ni 403 ; un rôle refusé obtient 403 {@code forbidden}, même quand la requête est invalide, car le refus précède sa
 * lecture. DenyByDefaultIT vérifie que cette liste couvre exactement les routes enregistrées, OpenApiContractIT qu'elle
 * couvre exactement les opérations du contrat.
 */
public class AuthorizationMatrixIT extends IntegrationTest {

    public static final String PUBLIC = "PUBLIC";
    public static final String AUTHENTICATED = "AUTHENTICATED";

    /**
     * Un endpoint et son accès : une constante de {@link Permissions}, {@link #PUBLIC} ou {@link #AUTHENTICATED}.
     * {@code pcrExempt} : un jeton {@code pcr = true} n'y est pas bloqué (spec §8.1) ; les endpoints de session ignorent
     * le Bearer et le sont donc aussi.
     */
    public record EndpointCase(HttpMethod method, String path, String access, boolean pcrExempt) {

        public String key() {
            return method.name() + " " + path;
        }

        @Override
        public String toString() {
            return key();
        }
    }

    public static final List<EndpointCase> CASES = List.of(
            new EndpointCase(POST, "/api/v1/auth/login", PUBLIC, true),
            new EndpointCase(POST, "/api/v1/auth/refresh", PUBLIC, true),
            new EndpointCase(POST, "/api/v1/auth/logout", PUBLIC, true),
            new EndpointCase(POST, "/api/v1/auth/change-password", AUTHENTICATED, true),
            new EndpointCase(GET, "/api/v1/me", AUTHENTICATED, true),
            new EndpointCase(GET, "/api/v1/users", Permissions.USER_READ, false),
            new EndpointCase(POST, "/api/v1/users", Permissions.USER_MANAGE, false),
            new EndpointCase(GET, "/api/v1/users/{id}", Permissions.USER_READ, false),
            new EndpointCase(PATCH, "/api/v1/users/{id}", Permissions.USER_MANAGE, false),
            new EndpointCase(DELETE, "/api/v1/users/{id}", Permissions.USER_MANAGE, false),
            new EndpointCase(POST, "/api/v1/users/{id}/reset-password", Permissions.USER_MANAGE, false),
            new EndpointCase(GET, "/api/v1/servers", Permissions.SERVER_READ, false),
            new EndpointCase(POST, "/api/v1/servers", Permissions.SERVER_WRITE, false),
            new EndpointCase(GET, "/api/v1/servers/{id}", Permissions.SERVER_READ, false),
            new EndpointCase(PUT, "/api/v1/servers/{id}", Permissions.SERVER_WRITE, false),
            new EndpointCase(DELETE, "/api/v1/servers/{id}", Permissions.SERVER_DELETE, false),
            new EndpointCase(GET, "/api/v1/servers/facets", Permissions.SERVER_READ, false),
            new EndpointCase(GET, "/api/v1/servers/{serverId}/deployments", Permissions.PROJECT_READ, false),
            new EndpointCase(GET, "/api/v1/projects", Permissions.PROJECT_READ, false),
            new EndpointCase(POST, "/api/v1/projects", Permissions.PROJECT_WRITE, false),
            new EndpointCase(GET, "/api/v1/projects/{id}", Permissions.PROJECT_READ, false),
            new EndpointCase(PUT, "/api/v1/projects/{id}", Permissions.PROJECT_WRITE, false),
            new EndpointCase(DELETE, "/api/v1/projects/{id}", Permissions.PROJECT_DELETE, false),
            new EndpointCase(POST, "/api/v1/projects/{projectId}/environments", Permissions.PROJECT_WRITE, false),
            new EndpointCase(PUT, "/api/v1/environments/{id}", Permissions.PROJECT_WRITE, false),
            new EndpointCase(DELETE, "/api/v1/environments/{id}", Permissions.PROJECT_DELETE, false),
            new EndpointCase(POST, "/api/v1/environments/{environmentId}/domains", Permissions.PROJECT_WRITE, false),
            new EndpointCase(DELETE, "/api/v1/domains/{id}", Permissions.PROJECT_WRITE, false),
            new EndpointCase(POST, "/api/v1/environments/{environmentId}/deployments", Permissions.PROJECT_WRITE, false),
            new EndpointCase(DELETE, "/api/v1/deployments/{id}", Permissions.PROJECT_WRITE, false),
            new EndpointCase(GET, "/api/v1/audit-logs", Permissions.AUDIT_READ, false));

    /** Clés {@code MÉTHODE motif}, dans le format des routes enregistrées. Une liste : un doublon ferait échouer la comparaison. */
    public static final List<String> CASES_AS_KEYS = CASES.stream().map(EndpointCase::key).toList();

    private static final Set<HttpMethod> WITH_BODY = Set.of(POST, PUT, PATCH);

    static Stream<EndpointCase> protectedCases() {
        return CASES.stream().filter(c -> !c.access().equals(PUBLIC));
    }

    static Stream<Arguments> casesTimesRoles() {
        return protectedCases().flatMap(c -> Arrays.stream(RoleCode.values()).map(role -> Arguments.of(c, role)));
    }

    static Stream<EndpointCase> nonExemptCases() {
        return CASES.stream().filter(c -> !c.pcrExempt());
    }

    static Stream<EndpointCase> exemptProtectedCases() {
        return protectedCases().filter(EndpointCase::pcrExempt);
    }

    @ParameterizedTest(name = "{1} {0}")
    @MethodSource("casesTimesRoles")
    void accessFollowsMatrix(EndpointCase c, RoleCode role) {
        Response response = call(c.method(), concrete(c.path()), minimalBody(c.method()), tokenFor(role));

        if (allowed(c, role)) {
            assertThat(response.status()).as(response.body()).isNotIn(401, 403);
        } else {
            assertProblem(response, 403, "forbidden");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonExemptCases")
    void pcrBlocks(EndpointCase c) {
        String token = fixtures.accessTokenWithPcr(fixtures.createUser(RoleCode.SUPER_ADMIN));

        assertProblem(call(c.method(), concrete(c.path()), minimalBody(c.method()), token), 403, "password-change-required");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exemptProtectedCases")
    void pcrExemptCasesStayReachable(EndpointCase c) {
        String token = fixtures.accessTokenWithPcr(fixtures.createUser(RoleCode.VIEWER));

        Response response = call(c.method(), concrete(c.path()), minimalBody(c.method()), token);

        assertThat(response.status()).as(response.body()).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("protectedCases")
    void noTokenIs401(EndpointCase c) {
        assertProblem(call(c.method(), concrete(c.path()), minimalBody(c.method()), null), 401, "unauthorized");
    }

    /**
     * Le refus précède la lecture de la requête : sans la permission, un corps illisible, mal formé ou invalide et une
     * variable de chemin invalide donnent 403, et ne révèlent donc pas les règles de validation. Témoin : la même requête
     * avec la permission répond 400, elle est donc bien invalide.
     */
    @ParameterizedTest(name = "{0} / {1} {2} {3}")
    @CsvSource(delimiter = '|', nullValues = "NONE", value = {
        "VIEWER    | INFRA_ADMIN | POST   | /api/v1/servers                | {\"name\":",
        "VIEWER    | INFRA_ADMIN | POST   | /api/v1/servers                | {}",
        "DEVELOPER | INFRA_ADMIN | PUT    | /api/v1/servers/not-a-uuid     | {}",
        "DEVOPS    | INFRA_ADMIN | DELETE | /api/v1/servers/not-a-uuid     | NONE",
        "DEVELOPER | SUPER_ADMIN | GET    | /api/v1/users/not-a-uuid       | NONE",
        "INFRA_ADMIN | SUPER_ADMIN | PATCH | /api/v1/users/not-a-uuid      | {\"fullName\": null}",
        "VIEWER    | DEVOPS      | POST   | /api/v1/projects               | []",
        "DEVELOPER | DEVOPS      | DELETE | /api/v1/environments/not-a-uuid | NONE"
    })
    void refusalComesBeforeReadingTheRequest(RoleCode refused, RoleCode granted, HttpMethod method, String path, String body) {
        assertProblem(call(method, path, body, tokenFor(refused)), 403, "forbidden");
        assertProblem(call(method, path, body, tokenFor(granted)), 400, "validation-error");
    }

    private static boolean allowed(EndpointCase c, RoleCode role) {
        return c.access().equals(AUTHENTICATED) || PermissionMatrixIT.EXPECTED.get(role).contains(c.access());
    }

    private static String minimalBody(HttpMethod method) {
        return WITH_BODY.contains(method) ? "{}" : null;
    }

    /** Variables de chemin remplacées par des UUID aléatoires : aucune requête ne touche une donnée existante. */
    private static String concrete(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
    }

    private Response call(HttpMethod method, String path, String body, String token) {
        var request = http.method(method).uri(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        EntityExchangeResult<String> result = request.exchange().expectBody(String.class).returnResult();
        return new Response(result.getStatus().value(), result.getResponseHeaders(), result.getResponseBody());
    }

    private static void assertProblem(Response response, int status, String type) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().getContentType()).isNotNull()
                .matches(contentType -> contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:" + type);
    }
}
