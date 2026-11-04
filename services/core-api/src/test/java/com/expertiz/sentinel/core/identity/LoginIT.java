package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.support.AuthClient;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** {@code POST /api/v1/auth/login} (spec §8.4) : succès, échecs indiscernables, comptes refusés, validation de la requête. */
class LoginIT extends IntegrationTest {

    static final String WRONG_PASSWORD = "Wrong-Password-1!";

    @Autowired
    Fingerprints fingerprints;

    @Autowired
    PasswordEncoder passwordEncoder;

    TestUser user;
    String ip;

    @BeforeEach
    void user() {
        user = fixtures.createUser(RoleCode.DEVOPS);
        ip = Fixtures.uniqueIp();
    }

    private String auditReason(String correlationId) {
        return ownerJdbc()
                .sql("SELECT details ->> 'reason' FROM audit_logs WHERE correlation_id = :c AND action = 'LOGIN_FAILED'")
                .param("c", correlationId)
                .query(String.class)
                .single();
    }

    private long throttleRows(String clientIp) {
        return ownerJdbc().sql("SELECT count(*) FROM login_throttles WHERE ip_fp = :ip")
                .param("ip", fingerprints.ip(clientIp))
                .query(Long.class)
                .single();
    }

    private static void assertInvalidCredentials(Response response) {
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getContentType()).matches(t -> t.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:unauthorized");
        assertThat((String) response.json("$.detail")).isEqualTo("Identifiants invalides.");
        assertThat(response.setCookie()).isNull();
    }

    @Test
    void successReturnsTokensAndCookie() {
        Response response = auth.login(user.email(), user.password(), ip);

        assertThat(response.status()).isEqualTo(200);
        assertThat((String) response.json("$.tokenType")).isEqualTo("Bearer");
        assertThat((Integer) response.json("$.expiresIn")).isEqualTo(900);
        assertThat((String) response.json("$.user.id")).isEqualTo(user.id().toString());
        assertThat((String) response.json("$.user.email")).isEqualTo(user.email());
        assertThat((String) response.json("$.user.role")).isEqualTo("DEVOPS");
        assertThat((List<String>) response.json("$.user.permissions"))
                .containsExactly("project:delete", "project:read", "project:write", "server:read", "server:write");
        assertThat((Boolean) response.json("$.user.mustChangePassword")).isFalse();
        assertThat(response.setCookie())
                .startsWith("sentinel_rt=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=86400");
        assertThat(response.headers().getCacheControl()).contains("no-store");

        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(response.accessToken()))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void refreshTokenIsStoredAsAHashOnly() throws Exception {
        String cookie = auth.login(user.email(), user.password(), ip).refreshCookie();

        assertThat(cookie).matches("[A-Za-z0-9_-]{43}");
        Map<String, Object> row = ownerJdbc()
                .sql("""
                        SELECT token_hash, session_started_at, issued_at, expires_at, host(ip) AS ip, revoked_at
                          FROM refresh_tokens WHERE user_id = :id""")
                .param("id", user.id())
                .query()
                .singleRow();
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cookie.getBytes(StandardCharsets.US_ASCII)));
        assertThat(row).containsEntry("token_hash", sha256).containsEntry("ip", ip).containsEntry("revoked_at", null);
        assertThat(((Timestamp) row.get("session_started_at")).toInstant()).isEqualTo(clock.instant());
        assertThat(((Timestamp) row.get("issued_at")).toInstant()).isEqualTo(clock.instant());
        assertThat(((Timestamp) row.get("expires_at")).toInstant()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
        assertThat(ownerJdbc().sql("SELECT string_agg(r::text, '|') FROM refresh_tokens r WHERE user_id = :id")
                        .param("id", user.id())
                        .query(String.class)
                        .single())
                .doesNotContain(cookie);
    }

    @Test
    void identifierIsNormalizedBeforeLookup() {
        assertThat(auth.login("  " + user.email().toUpperCase(Locale.ROOT) + " ", user.password(), ip).status()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownIdentifierAreIndistinguishable() {
        Response wrong = auth.login(user.email(), WRONG_PASSWORD, ip);
        Response unknown = auth.login(Fixtures.uniqueEmail(), WRONG_PASSWORD, Fixtures.uniqueIp());

        assertInvalidCredentials(wrong);
        assertInvalidCredentials(unknown);
        Map<String, Object> wrongBody = JsonPath.read(wrong.body(), "$");
        Map<String, Object> unknownBody = JsonPath.read(unknown.body(), "$");
        wrongBody.remove("correlationId");
        unknownBody.remove("correlationId");
        assertThat(wrongBody).isEqualTo(unknownBody);
    }

    @Test
    void unknownIdentifierCostsAPasswordVerification() {
        // Sans vérification factice, un identifiant inconnu répondrait plus vite d'une vérification Argon2 entière
        // (spec §8.4). L'écart toléré est calibré sur le coût d'Argon2 mesuré sur ce poste.
        String hash = passwordEncoder.encode(WRONG_PASSWORD);
        for (int i = 0; i < 3; i++) {
            auth.login(user.email(), WRONG_PASSWORD, Fixtures.uniqueIp());
            auth.login(Fixtures.uniqueEmail(), WRONG_PASSWORD, Fixtures.uniqueIp());
            passwordEncoder.matches(user.password(), hash);
        }
        int rounds = 9;
        long[] wrong = new long[rounds];
        long[] unknown = new long[rounds];
        long[] argon2 = new long[rounds];
        for (int i = 0; i < rounds; i++) {
            long start = System.nanoTime();
            auth.login(user.email(), WRONG_PASSWORD, Fixtures.uniqueIp());
            wrong[i] = System.nanoTime() - start;
            start = System.nanoTime();
            auth.login(Fixtures.uniqueEmail(), WRONG_PASSWORD, Fixtures.uniqueIp());
            unknown[i] = System.nanoTime() - start;
            start = System.nanoTime();
            passwordEncoder.matches(user.password(), hash);
            argon2[i] = System.nanoTime() - start;
        }

        assertThat(Math.abs(median(unknown) - median(wrong))).isLessThan(median(argon2) / 2);
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    @Test
    void disabledAccountRefusedWithoutIncrement() {
        appJdbc().sql("UPDATE users SET status = 'DISABLED' WHERE id = :id").param("id", user.id()).update();
        String correlationId = AuthClient.newRequestId();

        assertInvalidCredentials(auth.login(user.email(), user.password(), ip, correlationId));
        assertThat(auditReason(correlationId)).isEqualTo("ACCOUNT_DISABLED");
        assertThat(throttleRows(ip)).isZero();
    }

    @Test
    void expiredTemporaryPasswordRefusedWithoutIncrement() {
        appJdbc().sql("UPDATE users SET password_expires_at = :at, must_change_password = true WHERE id = :id")
                .param("at", Timestamp.from(clock.instant().minusSeconds(1)))
                .param("id", user.id())
                .update();
        String correlationId = AuthClient.newRequestId();

        assertInvalidCredentials(auth.login(user.email(), user.password(), ip, correlationId));
        assertThat(auditReason(correlationId)).isEqualTo("TEMP_PASSWORD_EXPIRED");
        assertThat(throttleRows(ip)).isZero();
    }

    @Test
    void validTemporaryPasswordOpensARestrictedSession() {
        appJdbc().sql("UPDATE users SET password_expires_at = :at, must_change_password = true WHERE id = :id")
                .param("at", Timestamp.from(clock.instant().plus(Duration.ofHours(1))))
                .param("id", user.id())
                .update();

        Response response = auth.login(user.email(), user.password(), ip);

        assertThat(response.status()).isEqualTo(200);
        assertThat((Boolean) response.json("$.user.mustChangePassword")).isTrue();
        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, bearer(response.accessToken()))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:password-change-required");
    }

    @Test
    void deletedAccountIsAnUnknownIdentifier() {
        appJdbc().sql("UPDATE users SET deleted_at = now(), status = 'DISABLED' WHERE id = :id").param("id", user.id()).update();
        String correlationId = AuthClient.newRequestId();

        assertInvalidCredentials(auth.login(user.email(), user.password(), ip, correlationId));
        assertThat(auditReason(correlationId)).isEqualTo("UNKNOWN_IDENTIFIER");
    }

    @Test
    void lastLoginUpdatedWithoutVersionBump() {
        long versionBefore = ownerJdbc().sql("SELECT version FROM users WHERE id = :id").param("id", user.id()).query(Long.class).single();

        assertThat(auth.login(user.email(), user.password(), ip).status()).isEqualTo(200);

        Map<String, Object> row = ownerJdbc().sql("SELECT version, last_login_at FROM users WHERE id = :id")
                .param("id", user.id())
                .query()
                .singleRow();
        assertThat(row.get("version")).isEqualTo(versionBefore);
        assertThat(((Timestamp) row.get("last_login_at")).toInstant()).isEqualTo(clock.instant());
    }

    @Test
    void foreignOriginIs403() {
        http.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", user.email(), "password", user.password()))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:origin-not-allowed");
    }

    @Test
    void textPlainIs415() {
        http.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.TEXT_PLAIN)
                .body("{\"email\":\"" + user.email() + "\",\"password\":\"" + user.password() + "\"}")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:unsupported-media-type");
    }

    @Test
    void blankFieldsAre400WithoutEcho() {
        Response response = auth.login("   ", "", ip);

        assertThat(response.status()).isEqualTo(400);
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:validation-error");
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactlyInAnyOrder("email", "password");
        assertThat(throttleRows(ip)).isZero();
    }

    @Test
    void oversizedValuesAre400BeforeAnyVerification() {
        // La borne de 1024 caractères limite le coût d'Argon2 ; la valeur n'est pas renvoyée.
        String password = "S3cr3t-".repeat(147);
        Response response = auth.login(user.email(), password, ip);

        assertThat(response.status()).isEqualTo(400);
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactly("password");
        assertThat(response.body()).doesNotContain("S3cr3t");
        assertThat(auth.login("a".repeat(250) + "@x.io", user.password(), ip).status()).isEqualTo(400);
        assertThat(throttleRows(ip)).isZero();
    }

    @Test
    void malformedJsonIs400WithoutEcho() {
        http.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"" + user.email() + "\",\"password\":\"S3cr3t-Passw0rd!")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("S3cr3t").doesNotContain(user.email()));
    }
}
