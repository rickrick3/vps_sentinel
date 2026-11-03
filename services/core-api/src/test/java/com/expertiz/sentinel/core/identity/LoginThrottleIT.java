package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * Limitation progressive (spec §8.4), avec l'horloge de test : portée compte + IP au 5ᵉ échec, portée IP au 20ᵉ, délais
 * de 30 s à 15 min, jamais permanente, sans effet depuis une autre IP.
 */
class LoginThrottleIT extends IntegrationTest {

    static final String WRONG_PASSWORD = "Wrong-Password-1!";

    @Autowired
    Fingerprints fingerprints;

    TestUser user;
    String ip;

    @BeforeEach
    void user() {
        user = fixtures.createUser(RoleCode.VIEWER);
        ip = Fixtures.uniqueIp();
    }

    private Response fail() {
        return auth.login(user.email(), WRONG_PASSWORD, ip);
    }

    private Response succeed() {
        return auth.login(user.email(), user.password(), ip);
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            assertThat(fail().status()).as("échec n° %d", i + 1).isEqualTo(401);
        }
    }

    private static void assertThrottled(Response response, long retryAfterSeconds) {
        assertThat(response.status()).isEqualTo(429);
        assertThat(response.headers().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo(String.valueOf(retryAfterSeconds));
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:login-throttled");
        assertThat((String) response.json("$.detail"))
                .isEqualTo("Trop de tentatives. Réessayez dans " + retryAfterSeconds + " secondes.");
    }

    private Optional<Map<String, Object>> throttleRow(String scope, String identifier, String clientIp) {
        return ownerJdbc()
                .sql("""
                        SELECT failure_count, blocked_until FROM login_throttles
                         WHERE scope = :scope AND coalesce(account_fp, '') = :account AND ip_fp = :ip""")
                .param("scope", scope)
                .param("account", identifier == null ? "" : fingerprints.identifier(identifier))
                .param("ip", fingerprints.ip(clientIp))
                .query()
                .listOfRows()
                .stream()
                .findFirst();
    }

    private long auditCount(String action) {
        return ownerJdbc().sql("SELECT count(*) FROM audit_logs WHERE target_id = :id AND action = :action")
                .param("id", user.id())
                .param("action", action)
                .query(Long.class)
                .single();
    }

    @Test
    void fourFailuresDoNotBlock() {
        failTimes(4);

        assertThat(succeed().status()).isEqualTo(200);
    }

    @Test
    void fifthFailureBlocks30s() {
        failTimes(5);

        assertThrottled(fail(), 30);
        assertThrottled(succeed(), 30);
    }

    @Test
    void progressiveDelaysCapAt15MinAndNeverPermanent() {
        failTimes(5);
        long block = 30;
        for (long expected : new long[] {60, 120, 240, 480, 900, 900}) {
            clock.advance(Duration.ofSeconds(block));
            assertThat(fail().status()).isEqualTo(401);
            assertThrottled(fail(), expected);
            block = expected;
        }

        clock.advance(Duration.ofSeconds(block));
        assertThat(succeed().status()).isEqualTo(200);
    }

    @Test
    void retryAfterIsRoundedUp() {
        failTimes(5);
        clock.advance(Duration.ofMillis(10_500));

        assertThrottled(fail(), 20);
    }

    @Test
    void blockedAttemptsNeitherVerifyNorExtend() {
        failTimes(5);
        Map<String, Object> before = throttleRow("ACCOUNT_IP", user.email(), ip).orElseThrow();
        long failuresAudited = auditCount("LOGIN_FAILED");

        clock.advance(Duration.ofSeconds(10));
        assertThrottled(succeed(), 20);
        assertThrottled(fail(), 20);

        assertThat(throttleRow("ACCOUNT_IP", user.email(), ip)).contains(before);
        assertThat(auditCount("LOGIN_FAILED")).isEqualTo(failuresAudited);
        clock.advance(Duration.ofSeconds(20));
        assertThat(succeed().status()).isEqualTo(200);
    }

    @Test
    void otherIpUnaffected() {
        failTimes(5);

        assertThat(auth.login(user.email(), user.password(), Fixtures.uniqueIp()).status()).isEqualTo(200);
        assertThat(succeed().status()).isEqualTo(429);
    }

    @Test
    void unknownAccountIsThrottledLikeAKnownOne() {
        String unknown = Fixtures.uniqueEmail();
        for (int i = 0; i < 5; i++) {
            assertThat(auth.login(unknown, WRONG_PASSWORD, ip).status()).isEqualTo(401);
        }

        assertThrottled(auth.login(unknown, WRONG_PASSWORD, ip), 30);
    }

    @Test
    void ipScopeBlocksAfter20Failures() {
        for (int i = 0; i < 20; i++) {
            assertThat(auth.login(Fixtures.uniqueEmail(), WRONG_PASSWORD, ip).status()).isEqualTo(401);
        }

        assertThrottled(auth.login(Fixtures.uniqueEmail(), WRONG_PASSWORD, ip), 30);
        assertThrottled(succeed(), 30);
        assertThat(throttleRow("IP", null, ip).orElseThrow()).containsEntry("failure_count", 20);
    }

    @Test
    void successResetsAccountIpNotIpScope() {
        failTimes(3);

        assertThat(succeed().status()).isEqualTo(200);

        assertThat(throttleRow("ACCOUNT_IP", user.email(), ip)).isEmpty();
        assertThat(throttleRow("IP", null, ip).orElseThrow()).containsEntry("failure_count", 3);
    }

    @Test
    void counterResetsAfterOneHour() {
        failTimes(3);
        clock.advance(Duration.ofMinutes(61));

        failTimes(1);

        assertThat(throttleRow("ACCOUNT_IP", user.email(), ip).orElseThrow()).containsEntry("failure_count", 1);
        assertThat(throttleRow("IP", null, ip).orElseThrow()).containsEntry("failure_count", 1);
    }

    @Test
    void parallelFailuresCountedExactly() throws Exception {
        // Identifiants distincts depuis une même IP : sous le seuil de la portée IP, aucun blocage ne peut écourter la
        // série ; le compteur de cette portée doit valoir exactement le nombre d'échecs concurrents.
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return auth.login(Fixtures.uniqueEmail(), WRONG_PASSWORD, ip).status();
                }));
            }
            start.countDown();
            for (Future<Integer> status : statuses) {
                assertThat(status.get()).isEqualTo(401);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(throttleRow("IP", null, ip).orElseThrow()).containsEntry("failure_count", threads);
    }
}
