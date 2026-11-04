package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.TemporaryPasswordGenerator;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Administration des utilisateurs (spec §8.8, §10.1, §10.2) par un SUPER_ADMIN, sauf mention contraire : création avec
 * mot de passe temporaire, liste filtrée, modification partielle, garde-fous, réinitialisation, suppression logique.
 */
@ExtendWith(OutputCaptureExtension.class)
class UsersIT extends IntegrationTest {

    @Autowired
    Fingerprints fingerprints;

    TestUser admin;
    String adminToken;

    @BeforeEach
    void admin() {
        admin = fixtures.createUser(RoleCode.SUPER_ADMIN);
        adminToken = fixtures.accessToken(admin);
    }

    private Response call(String token, HttpMethod method, String uri, Object body) {
        var request = http.method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        EntityExchangeResult<String> result = request.exchange().expectBody(String.class).returnResult();
        return new Response(result.getStatus().value(), result.getResponseHeaders(), result.getResponseBody());
    }

    private Response create(String email, String fullName, String role) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("fullName", fullName);
        body.put("role", role);
        return call(adminToken, HttpMethod.POST, "/api/v1/users", body);
    }

    private Response get(UUID id) {
        return call(adminToken, HttpMethod.GET, "/api/v1/users/" + id, null);
    }

    private long version(UUID id) {
        return ((Number) get(id).json("$.version")).longValue();
    }

    private Response patch(String token, UUID id, Map<String, Object> changes) {
        return call(token, HttpMethod.PATCH, "/api/v1/users/" + id, changes);
    }

    private Response patch(UUID id, String field, Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(field, value);
        body.put("version", version(id));
        return patch(adminToken, id, body);
    }

    private Map<String, Object> audit(UUID target, String action) {
        return ownerJdbc()
                .sql("""
                        SELECT actor_type, actor_user_id, details::text AS details,
                               details -> 'changes' -> 'role' ->> 'from' AS role_from,
                               details -> 'changes' -> 'role' ->> 'to' AS role_to,
                               details -> 'changes' -> 'status' ->> 'to' AS status_to,
                               details -> 'changes' -> 'fullName' ->> 'to' AS full_name_to
                          FROM audit_logs WHERE target_id = :id AND action = :action""")
                .param("id", target)
                .param("action", action)
                .query()
                .singleRow();
    }

    private static void assertProblem(Response response, int status, String type) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:" + type);
    }

    private static List<String> fields(Response response) {
        return response.json("$.errors[*].field");
    }

    // Création

    @Test
    void createReturnsTemporaryPasswordOnce(CapturedOutput output) {
        String email = Fixtures.uniqueEmail();

        Response created = create(email, "Nouvel Utilisateur", "DEVOPS");

        assertThat(created.status()).isEqualTo(201);
        UUID id = UUID.fromString(created.json("$.user.id"));
        assertThat(created.headers().getLocation()).hasToString("/api/v1/users/" + id);
        String temporary = created.json("$.temporaryPassword");
        assertThat(temporary).hasSize(16).matches("[" + TemporaryPasswordGenerator.ALPHABET + "]{16}");
        assertThat(Instant.parse(created.json("$.temporaryPasswordExpiresAt"))).isEqualTo(clock.instant().plus(Duration.ofHours(72)));
        assertThat((String) created.json("$.user.role")).isEqualTo("DEVOPS");
        assertThat((String) created.json("$.user.status")).isEqualTo("ACTIVE");
        assertThat((Boolean) created.json("$.user.mustChangePassword")).isTrue();
        assertThat(created.headers().getCacheControl()).contains("no-store");

        Response read = get(id);
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.body()).doesNotContain(temporary).doesNotContain("temporaryPassword\"").doesNotContain("passwordHash");

        Response login = auth.login(email, temporary, Fixtures.uniqueIp());
        assertThat(login.status()).isEqualTo(200);
        assertThat((Boolean) login.json("$.user.mustChangePassword")).isTrue();

        Map<String, Object> audit = audit(id, "USER_CREATED");
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_user_id", admin.id());
        assertThat((String) audit.get("details")).contains(email).contains("DEVOPS").doesNotContain(temporary);
        assertThat(output.getAll()).doesNotContain(temporary);
    }

    @Test
    void emailNormalizedOnCreate() {
        String email = Fixtures.uniqueEmail();

        Response created = create("  " + email.toUpperCase(Locale.ROOT) + " ", "Alice", "VIEWER");

        assertThat(created.status()).isEqualTo(201);
        assertThat((String) created.json("$.user.email")).isEqualTo(email);
    }

    @Test
    void duplicateEmailIs409OnEmail() {
        String email = Fixtures.uniqueEmail();
        create(email, "Premier", "VIEWER");

        Response duplicate = create(email.toUpperCase(Locale.ROOT), "Second", "VIEWER");

        assertProblem(duplicate, 409, "duplicate");
        assertThat(fields(duplicate)).containsExactly("email");
    }

    @Test
    void createValidation() {
        Response response = create("pas-un-email", "Bob\u0007", "ROOT");

        assertProblem(response, 400, "validation-error");
        assertThat(fields(response)).containsExactlyInAnyOrder("email", "fullName", "role");
        assertThat(fields(create(Fixtures.uniqueEmail(), "x".repeat(121), "VIEWER"))).containsExactly("fullName");
        assertThat(fields(create(Fixtures.uniqueEmail(), "   ", "VIEWER"))).containsExactly("fullName");
        assertThat(fields(call(adminToken, HttpMethod.POST, "/api/v1/users", Map.of()))).containsExactlyInAnyOrder("email", "fullName", "role");
    }

    // Lecture

    @Test
    void listFiltersSortsAndPaginates() {
        String marker = "Zq" + Long.toHexString(System.nanoTime());
        UUID alice = UUID.fromString(create(marker.toLowerCase(Locale.ROOT) + "-alice@sentinel.test", marker + " Alice", "DEVOPS").json("$.user.id"));
        UUID bob = UUID.fromString(create(Fixtures.uniqueEmail(), marker + " Bob", "VIEWER").json("$.user.id"));
        create(Fixtures.uniqueEmail(), marker + " Chloé", "VIEWER");
        patch(bob, "status", "DISABLED");

        Response all = call(adminToken, HttpMethod.GET, "/api/v1/users?q=" + marker.toUpperCase(Locale.ROOT), null);
        assertThat(all.status()).isEqualTo(200);
        assertThat((List<String>) all.json("$.items[*].fullName")).containsExactly(marker + " Alice", marker + " Bob", marker + " Chloé");
        assertThat((Integer) all.json("$.totalItems")).isEqualTo(3);

        assertThat((List<String>) call(adminToken, HttpMethod.GET, "/api/v1/users?q=" + marker + "&role=VIEWER", null).json("$.items[*].fullName"))
                .containsExactly(marker + " Bob", marker + " Chloé");
        assertThat((List<String>) call(adminToken, HttpMethod.GET, "/api/v1/users?q=" + marker + "&status=DISABLED", null).json("$.items[*].id"))
                .containsExactly(bob.toString());
        assertThat((List<String>) call(adminToken, HttpMethod.GET, "/api/v1/users?q=" + marker.toLowerCase(Locale.ROOT) + "-alice@", null).json("$.items[*].id"))
                .containsExactly(alice.toString());
        assertThat((List<String>) call(adminToken, HttpMethod.GET, "/api/v1/users?q=" + marker + "&sort=fullName,desc", null).json("$.items[*].fullName"))
                .containsExactly(marker + " Chloé", marker + " Bob", marker + " Alice");
        assertThat(call(adminToken, HttpMethod.GET, "/api/v1/users?q=" + marker + "&sort=lastLoginAt,desc", null).status()).isEqualTo(200);

        Response page = call(adminToken, HttpMethod.GET, "/api/v1/users?q=" + marker + "&page=1&size=2", null);
        assertThat((List<String>) page.json("$.items[*].fullName")).containsExactly(marker + " Chloé");
        assertThat((Integer) page.json("$.page")).isEqualTo(1);
        assertThat((Integer) page.json("$.size")).isEqualTo(2);
        assertThat((Integer) page.json("$.totalPages")).isEqualTo(2);
        assertThat(page.body()).doesNotContain("passwordHash").doesNotContain("$argon2id$");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            sort=passwordHash      | sort
            sort=email,sideways    | sort
            size=101               | size
            page=-1                | page
            role=ROOT              | role
            status=LOCKED          | status
            """)
    void invalidListParametersAre400(String query, String field) {
        Response response = call(adminToken, HttpMethod.GET, "/api/v1/users?" + query, null);

        assertProblem(response, 400, "validation-error");
        assertThat(fields(response)).containsExactly(field);
    }

    @Test
    void searchWildcardsAreLiteral() {
        // « %%_ » correspondrait à tout le monde s'il n'était pas échappé dans le LIKE.
        http.get()
                .uri("/api/v1/users?q={q}", "%%_")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalItems").isEqualTo(0);
    }

    @Test
    void unknownOrDeletedUserIs404() {
        assertProblem(get(UUID.randomUUID()), 404, "not-found");
        TestUser deleted = fixtures.createUser(RoleCode.VIEWER);
        appJdbc().sql("UPDATE users SET deleted_at = now(), status = 'DISABLED' WHERE id = :id").param("id", deleted.id()).update();

        assertProblem(get(deleted.id()), 404, "not-found");
        assertProblem(patch(adminToken, deleted.id(), Map.of("fullName", "X", "version", 0)), 404, "not-found");
        assertProblem(call(adminToken, HttpMethod.DELETE, "/api/v1/users/" + deleted.id(), null), 404, "not-found");
        assertProblem(call(adminToken, HttpMethod.POST, "/api/v1/users/" + deleted.id() + "/reset-password", null), 404, "not-found");
    }

    // Modification

    @Test
    void patchFullNameAudited() {
        TestUser target = fixtures.createUser(RoleCode.VIEWER);
        long before = version(target.id());

        Response response = patch(target.id(), "fullName", "  Nouveau Nom ");

        assertThat(response.status()).isEqualTo(200);
        assertThat((String) response.json("$.fullName")).isEqualTo("Nouveau Nom");
        assertThat(((Number) response.json("$.version")).longValue()).isEqualTo(before + 1);
        assertThat(audit(target.id(), "USER_UPDATED")).containsEntry("full_name_to", "Nouveau Nom");
    }

    @Test
    void patchStaleVersionIs409() {
        TestUser target = fixtures.createUser(RoleCode.VIEWER);
        long stale = version(target.id());
        patch(target.id(), "fullName", "Premier");

        assertProblem(patch(adminToken, target.id(), Map.of("fullName", "Second", "version", stale)), 409, "version-conflict");
        assertThat((String) get(target.id()).json("$.fullName")).isEqualTo("Premier");
    }

    @Test
    void patchValidationOnFields() {
        TestUser target = fixtures.createUser(RoleCode.VIEWER);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", null);
        body.put("email", "autre@sentinel.test");

        Response response = patch(adminToken, target.id(), body);

        assertProblem(response, 400, "validation-error");
        assertThat(fields(response)).containsExactlyInAnyOrder("fullName", "email", "version");
    }

    @Test
    void roleChangeRevokesSessionsAndAudits() {
        TestUser target = fixtures.createUser(RoleCode.VIEWER);
        String ip = Fixtures.uniqueIp();
        String cookie = auth.login(target.email(), target.password(), ip).refreshCookie();

        Response response = patch(target.id(), "role", "DEVOPS");

        assertThat(response.status()).isEqualTo(200);
        assertThat((String) response.json("$.role")).isEqualTo("DEVOPS");
        assertThat(auth.refresh(cookie, ip).status()).isEqualTo(401);
        assertThat(ownerJdbc().sql("SELECT revoked_reason FROM refresh_tokens WHERE user_id = :id")
                        .param("id", target.id())
                        .query(String.class)
                        .list())
                .containsOnly("ROLE_CHANGED");
        assertThat(audit(target.id(), "USER_ROLE_CHANGED"))
                .containsEntry("role_from", "VIEWER")
                .containsEntry("role_to", "DEVOPS")
                .containsEntry("actor_user_id", admin.id());
        Response login = auth.login(target.email(), target.password(), Fixtures.uniqueIp());
        assertThat((List<String>) login.json("$.user.permissions")).contains("server:write");
    }

    @Test
    void disableRevokesAndEnableAudited() {
        TestUser target = fixtures.createUser(RoleCode.VIEWER);
        String ip = Fixtures.uniqueIp();
        String cookie = auth.login(target.email(), target.password(), ip).refreshCookie();

        assertThat(patch(target.id(), "status", "DISABLED").status()).isEqualTo(200);
        assertThat(auth.refresh(cookie, ip).status()).isEqualTo(401);
        assertThat(auth.login(target.email(), target.password(), Fixtures.uniqueIp()).status()).isEqualTo(401);
        assertThat(audit(target.id(), "USER_DISABLED")).containsEntry("status_to", "DISABLED");

        assertThat(patch(target.id(), "status", "ACTIVE").status()).isEqualTo(200);
        assertThat(audit(target.id(), "USER_ENABLED")).containsEntry("status_to", "ACTIVE");
        assertThat(auth.login(target.email(), target.password(), Fixtures.uniqueIp()).status()).isEqualTo(200);
    }

    @Test
    void unchangedValuesAreNotAudited() {
        TestUser target = fixtures.createUser(RoleCode.VIEWER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", "VIEWER");
        body.put("status", "ACTIVE");
        body.put("version", version(target.id()));
        assertThat(patch(adminToken, target.id(), body).status()).isEqualTo(200);

        assertThat(ownerJdbc().sql("SELECT count(*) FROM audit_logs WHERE target_id = :id AND action LIKE 'USER_%'")
                        .param("id", target.id())
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void selfRoleChangeDisableDeleteAre409() {
        assertProblem(patch(admin.id(), "role", "VIEWER"), 409, "self-modification-forbidden");
        assertProblem(patch(admin.id(), "status", "DISABLED"), 409, "self-modification-forbidden");
        assertProblem(call(adminToken, HttpMethod.DELETE, "/api/v1/users/" + admin.id(), null), 409, "self-modification-forbidden");

        assertThat(patch(admin.id(), "fullName", "Mon Nouveau Nom").status()).isEqualTo(200);
        assertThat((String) get(admin.id()).json("$.role")).isEqualTo("SUPER_ADMIN");
    }

    // Réinitialisation et suppression

    @Test
    void resetPasswordRevokesClearsThrottlesAndExpires(CapturedOutput output) {
        TestUser target = fixtures.createUser(RoleCode.DEVELOPER);
        String ip = Fixtures.uniqueIp();
        String cookie = auth.login(target.email(), target.password(), ip).refreshCookie();
        for (int i = 0; i < 3; i++) {
            auth.login(target.email(), "Wrong-Password-1!", Fixtures.uniqueIp());
        }
        String accountFp = fingerprints.identifier(target.email());
        assertThat(throttleRows(accountFp)).isEqualTo(3);

        Response reset = call(adminToken, HttpMethod.POST, "/api/v1/users/" + target.id() + "/reset-password", null);

        assertThat(reset.status()).isEqualTo(200);
        String temporary = reset.json("$.temporaryPassword");
        assertThat(temporary).hasSize(16);
        assertThat(Instant.parse(reset.json("$.temporaryPasswordExpiresAt"))).isEqualTo(clock.instant().plus(Duration.ofHours(72)));
        assertThat(throttleRows(accountFp)).isZero();
        assertThat((String) audit(target.id(), "USER_PASSWORD_RESET").get("details")).contains("\"throttlesCleared\": 3");
        assertThat(auth.refresh(cookie, ip).status()).isEqualTo(401);
        assertThat(auth.login(target.email(), target.password(), Fixtures.uniqueIp()).status()).isEqualTo(401);
        Response login = auth.login(target.email(), temporary, Fixtures.uniqueIp());
        assertThat(login.status()).isEqualTo(200);
        assertThat((Boolean) login.json("$.user.mustChangePassword")).isTrue();
        assertThat(output.getAll()).doesNotContain(temporary);
    }

    @Test
    void temporaryPasswordExpiresAfter72Hours() {
        String email = Fixtures.uniqueEmail();
        String temporary = create(email, "Temporaire", "VIEWER").json("$.temporaryPassword");

        clock.advance(Duration.ofHours(72).plusSeconds(1));

        assertThat(auth.login(email, temporary, Fixtures.uniqueIp()).status()).isEqualTo(401);
    }

    @Test
    void deletedUserCannotLoginAndEmailReusable() {
        TestUser target = fixtures.createUser(RoleCode.DEVOPS);
        String ip = Fixtures.uniqueIp();
        String cookie = auth.login(target.email(), target.password(), ip).refreshCookie();

        assertThat(call(adminToken, HttpMethod.DELETE, "/api/v1/users/" + target.id(), null).status()).isEqualTo(204);

        assertProblem(get(target.id()), 404, "not-found");
        assertThat(auth.refresh(cookie, ip).status()).isEqualTo(401);
        assertThat(auth.login(target.email(), target.password(), Fixtures.uniqueIp()).status()).isEqualTo(401);
        Map<String, Object> audit = audit(target.id(), "USER_DELETED");
        assertThat((String) audit.get("details")).contains(target.email()).contains("DEVOPS");
        assertThat(ownerJdbc().sql("SELECT status FROM users WHERE id = :id").param("id", target.id()).query(String.class).single())
                .isEqualTo("DISABLED");
        assertThat(create(target.email(), "Réutilisé", "VIEWER").status()).isEqualTo(201);
    }

    @Test
    void patchBodyIsDocumented() {
        // Le contrat OpenAPI (tâche 17) et les types du frontend décrivent les champs du PATCH, pas un ObjectNode.
        Response docs = call(adminToken, HttpMethod.GET, "/v3/api-docs", null);

        assertThat((String) docs.json("$.paths['/api/v1/users/{id}'].patch.requestBody.content['application/json'].schema.$ref"))
                .endsWith("/PatchUserRequest");
        assertThat((Map<String, Object>) docs.json("$.components.schemas.PatchUserRequest.properties"))
                .containsOnlyKeys("fullName", "role", "status", "version");
    }

    // Autorisations

    @Test
    void infraAdminReadsButCannotManage() {
        String token = fixtures.accessToken(fixtures.createUser(RoleCode.INFRA_ADMIN));
        TestUser target = fixtures.createUser(RoleCode.VIEWER);

        assertThat(call(token, HttpMethod.GET, "/api/v1/users", null).status()).isEqualTo(200);
        assertThat(call(token, HttpMethod.GET, "/api/v1/users/" + target.id(), null).status()).isEqualTo(200);
        assertProblem(call(token, HttpMethod.POST, "/api/v1/users", Map.of("email", Fixtures.uniqueEmail(), "fullName", "X", "role", "VIEWER")), 403, "forbidden");
        assertProblem(patch(token, target.id(), Map.of("fullName", "X", "version", version(target.id()))), 403, "forbidden");
        assertProblem(call(token, HttpMethod.DELETE, "/api/v1/users/" + target.id(), null), 403, "forbidden");
        assertProblem(call(token, HttpMethod.POST, "/api/v1/users/" + target.id() + "/reset-password", null), 403, "forbidden");
        assertThat((String) get(target.id()).json("$.fullName")).isEqualTo("Utilisateur de test");
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"DEVOPS", "DEVELOPER", "VIEWER"})
    void rolesWithoutUserReadAreForbidden(RoleCode role) {
        String token = fixtures.accessToken(fixtures.createUser(role));

        assertProblem(call(token, HttpMethod.GET, "/api/v1/users", null), 403, "forbidden");
        assertProblem(call(token, HttpMethod.GET, "/api/v1/users/" + admin.id(), null), 403, "forbidden");
    }

    private long throttleRows(String accountFp) {
        return ownerJdbc().sql("SELECT count(*) FROM login_throttles WHERE scope = 'ACCOUNT_IP' AND account_fp = :a")
                .param("a", accountFp)
                .query(Long.class)
                .single();
    }
}
