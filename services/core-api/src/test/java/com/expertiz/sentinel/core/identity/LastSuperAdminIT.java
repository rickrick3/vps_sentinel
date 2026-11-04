package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IsolatedDatabaseIT;
import com.expertiz.sentinel.core.support.SentinelPostgres;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Dernier SUPER_ADMIN actif (spec §7.2, §8.8) : impossible de le rétrograder, de le désactiver ou de le supprimer,
 * même en concurrence. Base propre à la classe : la base partagée contient toujours d'autres SUPER_ADMIN.
 */
class LastSuperAdminIT extends IsolatedDatabaseIT {

    static final SentinelPostgres DATABASE = newDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        connect(registry, DATABASE);
    }

    @Override
    protected SentinelPostgres database() {
        return DATABASE;
    }

    @BeforeEach
    void noUsers() {
        ownerJdbc().sql("DELETE FROM refresh_tokens").update();
        ownerJdbc().sql("DELETE FROM login_throttles").update();
        ownerJdbc().sql("DELETE FROM user_roles").update();
        ownerJdbc().sql("DELETE FROM users").update();
    }

    private record Result(int status, String body) {

        String type() {
            return body == null || body.isEmpty() ? null : JsonPath.read(body, "$.type");
        }
    }

    private Result call(String token, HttpMethod method, String uri, Object body) {
        var request = http.method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        EntityExchangeResult<String> result = request.exchange().expectBody(String.class).returnResult();
        return new Result(result.getStatus().value(), result.getResponseBody());
    }

    private long version(UUID id) {
        return ownerJdbc().sql("SELECT version FROM users WHERE id = :id").param("id", id).query(Long.class).single();
    }

    private Result demote(String token, UUID target) {
        return call(token, HttpMethod.PATCH, "/api/v1/users/" + target, Map.of("role", "INFRA_ADMIN", "version", version(target)));
    }

    private long activeSuperAdmins() {
        return ownerJdbc()
                .sql("""
                        SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                         WHERE r.code = 'SUPER_ADMIN' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL""")
                .query(Long.class)
                .single();
    }

    @Test
    void lastSuperAdminCannotBeDemotedDisabledOrDeleted() {
        TestUser a = fixtures.createUser(RoleCode.SUPER_ADMIN);
        TestUser b = fixtures.createUser(RoleCode.SUPER_ADMIN);
        String tokenB = fixtures.accessToken(b);
        assertThat(demote(fixtures.accessToken(a), b.id()).status()).isEqualTo(200);

        // B n'est plus SUPER_ADMIN en base, mais son jeton reste valable au plus 15 min (spec §8.8) : A est le dernier.
        assertThat(demote(tokenB, a.id()).type()).isEqualTo("urn:sentinel:problem:last-super-admin");
        assertThat(call(tokenB, HttpMethod.PATCH, "/api/v1/users/" + a.id(), Map.of("status", "DISABLED", "version", version(a.id()))).type())
                .isEqualTo("urn:sentinel:problem:last-super-admin");
        Result delete = call(tokenB, HttpMethod.DELETE, "/api/v1/users/" + a.id(), null);
        assertThat(delete.status()).isEqualTo(409);
        assertThat(delete.type()).isEqualTo("urn:sentinel:problem:last-super-admin");

        assertThat(activeSuperAdmins()).isEqualTo(1);
    }

    @Test
    void renamingTheLastSuperAdminIsAllowed() {
        TestUser a = fixtures.createUser(RoleCode.SUPER_ADMIN);
        TestUser b = fixtures.createUser(RoleCode.SUPER_ADMIN);
        String tokenB = fixtures.accessToken(b);
        demote(fixtures.accessToken(a), b.id());

        assertThat(call(tokenB, HttpMethod.PATCH, "/api/v1/users/" + a.id(), Map.of("fullName", "Renommé", "version", version(a.id()))).status())
                .isEqualTo(200);
    }

    @Test
    void anotherActiveSuperAdminAllowsTheChange() {
        TestUser a = fixtures.createUser(RoleCode.SUPER_ADMIN);
        TestUser b = fixtures.createUser(RoleCode.SUPER_ADMIN);

        assertThat(demote(fixtures.accessToken(a), b.id()).status()).isEqualTo(200);
        assertThat(activeSuperAdmins()).isEqualTo(1);
    }

    @Test
    void disabledSuperAdminsDoNotCount() {
        TestUser a = fixtures.createUser(RoleCode.SUPER_ADMIN);
        TestUser b = fixtures.createUser(RoleCode.SUPER_ADMIN);
        TestUser disabled = fixtures.createUser(RoleCode.SUPER_ADMIN);
        appJdbc().sql("UPDATE users SET status = 'DISABLED' WHERE id = :id").param("id", disabled.id()).update();
        String tokenB = fixtures.accessToken(b);
        demote(fixtures.accessToken(a), b.id());

        assertThat(demote(tokenB, a.id()).type()).isEqualTo("urn:sentinel:problem:last-super-admin");
    }

    @Test
    void concurrentMutualDemotionKeepsOne() throws Exception {
        for (int iteration = 0; iteration < 20; iteration++) {
            noUsers();
            TestUser a = fixtures.createUser(RoleCode.SUPER_ADMIN);
            TestUser b = fixtures.createUser(RoleCode.SUPER_ADMIN);
            String tokenA = fixtures.accessToken(a);
            String tokenB = fixtures.accessToken(b);
            long versionA = version(a.id());
            long versionB = version(b.id());

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Result> results = new ArrayList<>();
            try {
                Future<Result> aDemotesB = pool.submit(() -> {
                    start.await();
                    return call(tokenA, HttpMethod.PATCH, "/api/v1/users/" + b.id(), Map.of("role", "VIEWER", "version", versionB));
                });
                Future<Result> bDemotesA = pool.submit(() -> {
                    start.await();
                    return call(tokenB, HttpMethod.PATCH, "/api/v1/users/" + a.id(), Map.of("role", "VIEWER", "version", versionA));
                });
                start.countDown();
                results.add(aDemotesB.get());
                results.add(bDemotesA.get());
            } finally {
                pool.shutdownNow();
            }

            assertThat(activeSuperAdmins()).as("itération %d", iteration).isEqualTo(1);
            assertThat(results).as("itération %d", iteration).extracting(Result::status).containsExactlyInAnyOrder(200, 409);
            assertThat(results).filteredOn(r -> r.status() == 409).extracting(Result::type)
                    .containsExactly("urn:sentinel:problem:last-super-admin");
        }
    }
}
