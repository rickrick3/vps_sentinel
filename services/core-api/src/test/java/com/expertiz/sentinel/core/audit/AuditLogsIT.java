package com.expertiz.sentinel.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.persistence.UuidV7;
import com.expertiz.sentinel.core.support.AuthClient;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Lecture du journal d'audit (spec §9.5, §10.1, §10.2) : {@code GET /api/v1/audit-logs}, permission {@code audit:read}.
 * Les lignes sont insérées directement, à des dates choisies. La base partagée contient l'audit de tous les autres
 * tests : chaque test isole ses lignes par un acteur ou une cible aléatoire.
 */
class AuditLogsIT extends IntegrationTest {

    private static final String PATH = "/api/v1/audit-logs";
    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");

    private String token;

    @BeforeEach
    void signIn() {
        token = tokenFor(RoleCode.INFRA_ADMIN);
    }

    @Test
    void filtersAndNewestFirst() {
        UUID actor = UUID.randomUUID();
        UUID server = UUID.randomUUID();
        UUID a = insert(UuidV7.next(), T0, actor, "SERVER_CREATED", "SERVER", server);
        UUID b = insert(UuidV7.next(), T0.plusSeconds(60), actor, "SERVER_UPDATED", "SERVER", server);
        UUID c = insert(UuidV7.next(), T0.plusSeconds(120), actor, "PROJECT_CREATED", "PROJECT", UUID.randomUUID());
        UUID d = insert(UuidV7.next(), T0.plusSeconds(180), actor, "SERVER_DELETED", "SERVER", UUID.randomUUID());
        String byActor = "?actorUserId=" + actor;

        assertThat(ids(list(byActor))).containsExactly(d, c, b, a);
        assertThat(ids(list(byActor + "&sort=occurredAt,desc"))).containsExactly(d, c, b, a);
        assertThat(ids(list(byActor + "&sort=occurredAt,asc"))).containsExactly(a, b, c, d);
        assertThat(ids(list(byActor + "&action=SERVER_UPDATED"))).containsExactly(b);
        assertThat(ids(list(byActor + "&targetType=SERVER"))).containsExactly(d, b, a);
        assertThat(ids(list("?targetId=" + server))).containsExactly(b, a);
        assertThat(ids(list("?targetType=SERVER&targetId=" + server))).containsExactly(b, a);
        assertThat(ids(list("?targetType=PROJECT&targetId=" + server))).isEmpty();
        assertThat(ids(list("?actorUserId=" + UUID.randomUUID()))).isEmpty();
    }

    /** {@code from} inclus, {@code to} exclu : deux intervalles consécutifs ne comptent jamais deux fois la même ligne. */
    @Test
    void fromIsInclusiveAndToExclusive() {
        UUID actor = UUID.randomUUID();
        UUID a = insert(UuidV7.next(), T0, actor, "LOGIN_SUCCEEDED", "USER", actor);
        UUID b = insert(UuidV7.next(), T0.plusSeconds(60), actor, "LOGOUT", "USER", actor);
        UUID c = insert(UuidV7.next(), T0.plusSeconds(120), actor, "LOGIN_SUCCEEDED", "USER", actor);
        String byActor = "?actorUserId=" + actor;

        assertThat(ids(list(byActor + "&from=" + T0.plusSeconds(60)))).containsExactly(c, b);
        assertThat(ids(list(byActor + "&to=" + T0.plusSeconds(60)))).containsExactly(a);
        assertThat(ids(list(byActor + "&from=" + T0 + "&to=" + T0.plusSeconds(120)))).containsExactly(b, a);
        assertThat(ids(list(byActor + "&from=" + T0.plusSeconds(1) + "&to=" + T0.plusSeconds(60)))).isEmpty();
        assertThat(ids(list(byActor + "&action=LOGIN_SUCCEEDED&from=" + T0.plusSeconds(1)))).containsExactly(c);
    }

    @Test
    void paginates() {
        UUID actor = UUID.randomUUID();
        List<UUID> rows = Stream.iterate(0, i -> i + 1).limit(5)
                .map(i -> insert(UuidV7.next(), T0.plusSeconds(i), actor, "LOGOUT", "USER", actor))
                .toList();

        Response first = list("?actorUserId=" + actor + "&size=2");
        Response last = list("?actorUserId=" + actor + "&size=2&page=2");

        assertThat(ids(first)).containsExactly(rows.get(4), rows.get(3));
        assertThat((Integer) first.json("$.page")).isZero();
        assertThat((Integer) first.json("$.size")).isEqualTo(2);
        assertThat((Integer) first.json("$.totalItems")).isEqualTo(5);
        assertThat((Integer) first.json("$.totalPages")).isEqualTo(3);
        assertThat(ids(last)).containsExactly(rows.get(0));
    }

    /** Lignes de même date : départagées par id, dans le sens du tri, pour qu'une page ne dépende pas du hasard. */
    @Test
    void equalTimestampsKeepAStableOrder() {
        UUID actor = UUID.randomUUID();
        List<UUID> ids = Stream.generate(UuidV7::next).limit(3).sorted(Comparator.comparing(UUID::toString)).toList();
        // Insertion dans le désordre : l'ordre affiché vient du tri, pas de l'ordre physique.
        for (UUID id : List.of(ids.get(1), ids.get(2), ids.get(0))) {
            insert(id, T0, actor, "LOGOUT", "USER", actor);
        }

        assertThat(ids(list("?actorUserId=" + actor))).containsExactly(ids.get(2), ids.get(1), ids.get(0));
        assertThat(ids(list("?actorUserId=" + actor + "&sort=occurredAt,asc"))).containsExactly(ids.get(0), ids.get(1), ids.get(2));
    }

    @Test
    void responseCarriesEveryColumn() {
        UUID id = UuidV7.next();
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ownerJdbc().sql("""
                        INSERT INTO audit_logs (id, occurred_at, actor_type, actor_user_id, actor_email, action, target_type,
                                                target_id, details, ip, correlation_id)
                        VALUES (:id, :at, 'USER', :actor, 'alice@sentinel.test', 'SERVER_UPDATED', 'SERVER', :target,
                                CAST(:details AS jsonb), CAST('2001:db8:0:0:0:0:0:1' AS inet), 'req-123')""")
                .param("id", id)
                .param("at", OffsetDateTime.ofInstant(T0, ZoneOffset.UTC))
                .param("actor", actor)
                .param("target", target)
                .param("details", "{\"changes\": {\"name\": {\"from\": \"a\", \"to\": \"b\"}}, \"count\": 3}")
                .update();

        Response response = list("?targetId=" + target);

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat((String) response.json("$.items[0].id")).isEqualTo(id.toString());
        assertThat((String) response.json("$.items[0].occurredAt")).isEqualTo("2026-01-15T10:00:00Z");
        assertThat((String) response.json("$.items[0].actorType")).isEqualTo("USER");
        assertThat((String) response.json("$.items[0].actorUserId")).isEqualTo(actor.toString());
        assertThat((String) response.json("$.items[0].actorEmail")).isEqualTo("alice@sentinel.test");
        assertThat((String) response.json("$.items[0].action")).isEqualTo("SERVER_UPDATED");
        assertThat((String) response.json("$.items[0].targetType")).isEqualTo("SERVER");
        assertThat((String) response.json("$.items[0].targetId")).isEqualTo(target.toString());
        // details est un objet JSON, pas une chaîne.
        assertThat((String) response.json("$.items[0].details.changes.name.from")).isEqualTo("a");
        assertThat((Integer) response.json("$.items[0].details.count")).isEqualTo(3);
        assertThat((String) response.json("$.items[0].ip")).isEqualTo("2001:db8::1");
        assertThat((String) response.json("$.items[0].correlationId")).isEqualTo("req-123");
    }

    @Test
    void anonymousRowsHaveNullActorAndEmptyDetails() {
        UUID target = UUID.randomUUID();
        ownerJdbc().sql("""
                        INSERT INTO audit_logs (id, occurred_at, actor_type, action, target_type, target_id)
                        VALUES (:id, :at, 'ANONYMOUS', 'LOGIN_FAILED', 'USER', :target)""")
                .param("id", UuidV7.next())
                .param("at", OffsetDateTime.ofInstant(T0, ZoneOffset.UTC))
                .param("target", target)
                .update();

        Response response = list("?targetId=" + target);

        assertThat((String) response.json("$.items[0].actorType")).isEqualTo("ANONYMOUS");
        assertThat((Object) response.json("$.items[0].actorUserId")).isNull();
        assertThat((Object) response.json("$.items[0].actorEmail")).isNull();
        assertThat((Object) response.json("$.items[0].ip")).isNull();
        assertThat((Object) response.json("$.items[0].correlationId")).isNull();
        assertThat((Map<?, ?>) response.json("$.items[0].details")).isEmpty();
    }

    /** Bout en bout : une écriture réelle apparaît avec son acteur, sa date, son IP et l'identifiant de la requête. */
    @Test
    void recordedEventsAreListed() {
        TestUser admin = fixtures.createUser(RoleCode.INFRA_ADMIN);
        String requestId = AuthClient.newRequestId();
        String name = Fixtures.uniqueName("VPS");
        EntityExchangeResult<String> created = http.post()
                .uri("/api/v1/servers")
                .header(HttpHeaders.AUTHORIZATION, bearer(fixtures.accessToken(admin)))
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        String serverId = new Response(201, created.getResponseHeaders(), created.getResponseBody()).json("$.id");

        Response response = list("?targetId=" + serverId);

        assertThat((List<String>) response.json("$.items[*].action")).containsExactly("SERVER_CREATED");
        assertThat((String) response.json("$.items[0].actorType")).isEqualTo("USER");
        assertThat((String) response.json("$.items[0].actorUserId")).isEqualTo(admin.id().toString());
        assertThat((String) response.json("$.items[0].actorEmail")).isEqualTo(admin.email());
        assertThat((String) response.json("$.items[0].targetType")).isEqualTo("SERVER");
        assertThat(Instant.parse(response.json("$.items[0].occurredAt"))).isEqualTo(clock.instant());
        assertThat((String) response.json("$.items[0].details.name")).isEqualTo(name);
        assertThat((String) response.json("$.items[0].ip")).isEqualTo("127.0.0.1");
        assertThat((String) response.json("$.items[0].correlationId")).isEqualTo(requestId);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "actorUserId=nope                                           | actorUserId",
        "targetId=42                                                | targetId",
        "from=2026-01-15                                            | from",
        "to=yesterday                                               | to",
        "from=2026-01-15T10:00:00Z&to=2026-01-15T09:59:59Z          | to",
        "from=2026-01-15T10:00:00Z&to=2026-01-15T10:00:00Z          | to",
        "sort=action,asc                                            | sort",
        "sort=occurredAt,sideways                                   | sort",
        "size=101                                                   | size",
        "page=-1                                                    | page"
    })
    void invalidParametersAre400(String query, String field) {
        Response response = list("?" + query);

        assertThat(response.status()).as(response.body()).isEqualTo(400);
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:validation-error");
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactly(field);
    }

    /** Longueurs des colonnes (spec §9.5) : au-delà, le filtre ne pourrait rien trouver. */
    @Test
    void overlongFiltersAre400() {
        assertThat(list("?action=" + "A".repeat(64)).status()).isEqualTo(200);
        assertThat(list("?targetType=" + "A".repeat(32)).status()).isEqualTo(200);

        Response response = list("?action=" + "A".repeat(65) + "&targetType=" + "A".repeat(33));

        assertThat(response.status()).isEqualTo(400);
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactlyInAnyOrder("action", "targetType");
    }

    @Test
    void everyInvalidFilterIsReported() {
        Response response = list("?actorUserId=nope&targetId=nope&from=never");

        assertThat(response.status()).isEqualTo(400);
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactlyInAnyOrder("actorUserId", "targetId", "from");
    }

    @Test
    void developerForbidden() {
        Response response = api(tokenFor(RoleCode.DEVELOPER), HttpMethod.GET, PATH, null);

        assertThat(response.status()).isEqualTo(403);
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:forbidden");
    }

    /** Les autres rôles sont couverts par AuthorizationMatrixIT. */
    @Test
    void infraAdminAllowed() {
        assertThat(api(tokenFor(RoleCode.INFRA_ADMIN), HttpMethod.GET, PATH, null).status()).isEqualTo(200);
    }

    private UUID insert(UUID id, Instant at, UUID actor, String action, String targetType, UUID targetId) {
        ownerJdbc().sql("""
                        INSERT INTO audit_logs (id, occurred_at, actor_type, actor_user_id, action, target_type, target_id)
                        VALUES (:id, :at, 'USER', :actor, :action, :targetType, :targetId)""")
                .param("id", id)
                .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                .param("actor", actor)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .update();
        return id;
    }

    private Response list(String query) {
        return api(token, HttpMethod.GET, PATH + query, null);
    }

    private static List<UUID> ids(Response response) {
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        List<String> ids = response.json("$.items[*].id");
        return ids.stream().map(UUID::fromString).toList();
    }
}
