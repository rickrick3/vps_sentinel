package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * {@code POST /api/v1/auth/refresh} (spec §8.2, §8.3) : rotation atomique, un seul successeur par jeton, réutilisation
 * détectée même après expiration, inactivité de 24 h et durée absolue de 7 jours.
 */
class RefreshIT extends IntegrationTest {

    TestUser user;
    String ip;
    String cookie;

    @BeforeEach
    void session() {
        clock.set(Instant.parse("2026-11-03T08:00:00Z"));
        user = fixtures.createUser(RoleCode.DEVOPS);
        ip = Fixtures.uniqueIp();
        cookie = auth.login(user.email(), user.password(), ip).refreshCookie();
    }

    static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII)));
    }

    private Map<String, Object> row(String rawToken) throws Exception {
        return ownerJdbc()
                .sql("""
                        SELECT id, family_id, revoked_reason, replaced_by, revoked_at, session_started_at
                          FROM refresh_tokens WHERE token_hash = :hash""")
                .param("hash", sha256(rawToken))
                .query()
                .singleRow();
    }

    private List<Map<String, Object>> family(UUID familyId) {
        return ownerJdbc().sql("SELECT id, revoked_reason, revoked_at FROM refresh_tokens WHERE family_id = :f")
                .param("f", familyId)
                .query()
                .listOfRows();
    }

    private long reuseAudits() {
        return ownerJdbc().sql("SELECT count(*) FROM audit_logs WHERE target_id = :id AND action = 'TOKEN_REUSE_DETECTED'")
                .param("id", user.id())
                .query(Long.class)
                .single();
    }

    private static void assertRejected(Response response) {
        assertThat(response.status()).isEqualTo(401);
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:unauthorized");
        assertThat((String) response.json("$.detail")).isEqualTo("Session expirée. Reconnectez-vous.");
        assertThat(response.setCookie()).startsWith("sentinel_rt=;").contains("Max-Age=0", "Path=/api/v1/auth");
    }

    @Test
    void rotates() throws Exception {
        Map<String, Object> before = row(cookie);

        Response response = auth.refresh(cookie, ip);

        assertThat(response.status()).isEqualTo(200);
        String next = response.refreshCookie();
        assertThat(next).isNotNull().isNotEqualTo(cookie);
        assertThat(response.setCookie()).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=86400");
        assertThat((String) response.json("$.tokenType")).isEqualTo("Bearer");
        assertThat((String) response.json("$.user.role")).isEqualTo("DEVOPS");
        Map<String, Object> rotated = row(cookie);
        Map<String, Object> successor = row(next);
        assertThat(rotated).containsEntry("revoked_reason", "ROTATED").containsEntry("replaced_by", successor.get("id"));
        assertThat(successor)
                .containsEntry("family_id", before.get("family_id"))
                .containsEntry("session_started_at", before.get("session_started_at"))
                .containsEntry("revoked_at", null);
        http.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(response.accessToken()))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void parallelRefreshExactlyOneSucceeds() throws Exception {
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return auth.refresh(cookie, ip).status();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 401).hasSize(threads - 1);
        // Les requêtes perdantes voient un jeton déjà tourné : la famille entière est révoquée, successeur compris.
        assertThat(family((UUID) row(cookie).get("family_id"))).allSatisfy(t -> assertThat(t.get("revoked_at")).isNotNull());
    }

    @Test
    void reuseRevokesFamilyEvenIfExpired() throws Exception {
        String next = auth.refresh(cookie, ip).refreshCookie();
        clock.advance(Duration.ofHours(25));
        String correlationId = AuthClient.newRequestId();

        assertRejected(auth.refresh(cookie, ip, correlationId));

        UUID familyId = (UUID) row(cookie).get("family_id");
        assertThat(family(familyId)).allSatisfy(t -> assertThat(t.get("revoked_at")).isNotNull());
        assertThat(row(next)).containsEntry("revoked_reason", "REUSE_DETECTED");
        Map<String, Object> audit = ownerJdbc()
                .sql("""
                        SELECT actor_type, actor_user_id, actor_email, target_type, target_id,
                               details ->> 'familyId' AS family_id, (details ->> 'secondsSinceRotation')::bigint AS seconds,
                               (details ->> 'sameIp')::boolean AS same_ip, (details ->> 'sameUserAgent')::boolean AS same_ua
                          FROM audit_logs WHERE action = 'TOKEN_REUSE_DETECTED' AND correlation_id = :c""")
                .param("c", correlationId)
                .query()
                .singleRow();
        assertThat(audit)
                .containsEntry("actor_type", "USER")
                .containsEntry("actor_user_id", user.id())
                .containsEntry("actor_email", user.email())
                .containsEntry("target_type", "USER")
                .containsEntry("target_id", user.id())
                .containsEntry("family_id", familyId.toString())
                .containsEntry("same_ip", true)
                .containsEntry("same_ua", true);
        assertThat((Long) audit.get("seconds")).isGreaterThanOrEqualTo(90_000);
        assertThat(reuseAudits()).isEqualTo(1);
    }

    @Test
    void reuseFromAnotherIpIsFlagged() throws Exception {
        auth.refresh(cookie, ip);
        String correlationId = AuthClient.newRequestId();

        assertRejected(auth.refresh(cookie, Fixtures.uniqueIp(), correlationId));

        assertThat(ownerJdbc()
                        .sql("SELECT (details ->> 'sameIp')::boolean FROM audit_logs WHERE correlation_id = :c")
                        .param("c", correlationId)
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void reuseAlsoCutsTheLegitimateSuccessor() {
        String next = auth.refresh(cookie, ip).refreshCookie();

        assertRejected(auth.refresh(cookie, ip));

        assertRejected(auth.refresh(next, ip));
    }

    @Test
    void reuseOnInactiveFamilyIsNotAudited() {
        String next = auth.refresh(cookie, ip).refreshCookie();
        assertThat(auth.logout(next, ip).status()).isEqualTo(204);

        assertRejected(auth.refresh(cookie, ip));

        assertThat(reuseAudits()).isZero();
    }

    @Test
    void validJustBeforeIdleExpiry() {
        clock.advance(Duration.ofHours(24).minusSeconds(1));

        assertThat(auth.refresh(cookie, ip).status()).isEqualTo(200);
    }

    @Test
    void idleExpiry() {
        clock.advance(Duration.ofHours(24).plusSeconds(1));

        assertRejected(auth.refresh(cookie, ip));
        assertThat(reuseAudits()).isZero();
    }

    @Test
    void absoluteExpiry() {
        Response last = null;
        for (int i = 1; i <= 7; i++) {
            clock.advance(Duration.ofHours(23));
            last = auth.refresh(cookie, ip);
            assertThat(last.status()).as("refresh n° %d", i).isEqualTo(200);
            cookie = last.refreshCookie();
        }
        // 161 h écoulées : il reste 7 h avant la limite absolue de 7 jours.
        assertThat(last.setCookie()).contains("Max-Age=25200");

        clock.advance(Duration.ofHours(7).plusSeconds(1));

        assertRejected(auth.refresh(cookie, ip));
    }

    @Test
    void disabledUserRejectedWithoutSuccessor() throws Exception {
        ownerJdbc().sql("UPDATE users SET status = 'DISABLED' WHERE id = :id").param("id", user.id()).update();

        assertRejected(auth.refresh(cookie, ip));

        Map<String, Object> presented = row(cookie);
        assertThat(presented).containsEntry("revoked_reason", "USER_DISABLED").containsEntry("replaced_by", null);
        assertThat(family((UUID) presented.get("family_id"))).hasSize(1);
    }

    @Test
    void deletedUserRejected() throws Exception {
        ownerJdbc().sql("UPDATE users SET status = 'DISABLED', deleted_at = now() WHERE id = :id").param("id", user.id()).update();

        assertRejected(auth.refresh(cookie, ip));

        assertThat(row(cookie)).containsEntry("revoked_reason", "USER_DISABLED");
    }

    @Test
    void claimsRecomputedAfterRoleChange() throws Exception {
        appJdbc().sql("UPDATE user_roles SET role_id = (SELECT id FROM roles WHERE code = 'VIEWER') WHERE user_id = :id")
                .param("id", user.id())
                .update();

        Response response = auth.refresh(cookie, ip);

        assertThat(response.status()).isEqualTo(200);
        assertThat((String) response.json("$.user.role")).isEqualTo("VIEWER");
        assertThat((List<String>) response.json("$.user.permissions")).containsExactly("project:read", "server:read");
        var claims = SignedJWT.parse(response.accessToken()).getJWTClaimsSet();
        assertThat(claims.getStringClaim("role")).isEqualTo("VIEWER");
        assertThat(claims.getStringListClaim("perms")).containsExactly("project:read", "server:read");
    }

    @Test
    void missingCookieIs401() {
        assertRejected(auth.refresh(null, ip));
    }

    @Test
    void unknownCookieIs401() {
        assertRejected(auth.refresh("dGhpcy1pcy1ub3QtYS1yZWFsLXJlZnJlc2gtdG9rZW4", ip));
    }

    @Test
    void originRequiredAndTokenUntouched() throws Exception {
        http.post()
                .uri("/api/v1/auth/refresh")
                .cookie(AuthClient.COOKIE, cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:origin-not-allowed");

        assertThat(row(cookie)).containsEntry("revoked_at", null);
    }
}
