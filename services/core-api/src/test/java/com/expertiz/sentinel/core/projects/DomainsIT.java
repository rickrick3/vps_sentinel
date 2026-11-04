package com.expertiz.sentinel.core.projects;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

/** Domaines (spec §9.4, §10.3) : normalisés, uniques au niveau global, au plus un domaine principal par environnement. */
class DomainsIT extends ProjectsTestBase {

    UUID projectId;
    UUID environmentId;

    @BeforeEach
    void environment() {
        projectId = createProject(Fixtures.uniqueName("Demo"));
        environmentId = createEnvironment(projectId, "prod");
    }

    private static String uniqueDomain() {
        return Fixtures.uniqueName("d").toLowerCase() + ".example.com";
    }

    private Response add(UUID environment, String hostname, boolean primary) {
        return api(token, HttpMethod.POST, "/api/v1/environments/" + environment + "/domains", Map.of("hostname", hostname, "isPrimary", primary));
    }

    private Map<String, Boolean> primaries(UUID environment) {
        Map<String, Boolean> result = new HashMap<>();
        ownerJdbc().sql("SELECT hostname, is_primary FROM domains WHERE environment_id = :e")
                .param("e", environment)
                .query((rs, n) -> result.put(rs.getString("hostname"), rs.getBoolean("is_primary")))
                .list();
        return result;
    }

    @Test
    void hostnameNormalizedAndGloballyUnique() {
        String hostname = uniqueDomain();

        Response added = add(environmentId, "  " + hostname.toUpperCase() + " ", false);

        assertThat(added.status()).as(added.body()).isEqualTo(201);
        assertThat((String) added.json("$.hostname")).isEqualTo(hostname);
        UUID other = createEnvironment(createProject(Fixtures.uniqueName("Other")), "prod");
        Response duplicate = add(other, hostname, false);
        assertProblem(duplicate, 409, "duplicate");
        assertThat((List<String>) duplicate.json("$.errors[*].field")).containsExactly("hostname");
        assertThat(auditDetails(UUID.fromString(added.json("$.id")), "DOMAIN_ADDED")).contains(hostname).contains(environmentId.toString());
    }

    @Test
    void primarySwitchKeepsSingle() {
        String a = uniqueDomain();
        String b = uniqueDomain();
        add(environmentId, a, true);

        add(environmentId, b, true);

        assertThat(primaries(environmentId)).containsEntry(a, false).containsEntry(b, true);
        assertThat((List<Boolean>) getProject(projectId).json("$.environments[0].domains[*].isPrimary")).containsExactlyInAnyOrder(false, true);
    }

    @Test
    void concurrentPrimarySwitchesKeepExactlyOne() throws Exception {
        for (int iteration = 0; iteration < 10; iteration++) {
            UUID environment = createEnvironment(projectId, "env" + iteration);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Integer>> statuses = new ArrayList<>();
            try {
                for (int i = 0; i < 2; i++) {
                    String hostname = uniqueDomain();
                    statuses.add(pool.submit(() -> {
                        start.await();
                        return add(environment, hostname, true).status();
                    }));
                }
                start.countDown();
                for (Future<Integer> status : statuses) {
                    assertThat(status.get()).as("itération %d", iteration).isEqualTo(201);
                }
            } finally {
                pool.shutdownNow();
            }
            assertThat(primaries(environment).values()).as("itération %d", iteration).containsExactlyInAnyOrder(true, false);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo.example.com.", "*.example.com", "bücher.example", "example", "-demo.example.com", "demo..example.com"})
    void invalidHostnames(String hostname) {
        Response response = add(environmentId, hostname, false);

        assertProblem(response, 400, "validation-error");
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactly("hostname");
    }

    @Test
    void punycodeAccepted() {
        assertThat(add(environmentId, "xn--bcher-kva." + uniqueDomain(), false).status()).isEqualTo(201);
    }

    @Test
    void removeAudited() {
        String hostname = uniqueDomain();
        UUID id = created(add(environmentId, hostname, true));

        assertThat(api(token, HttpMethod.DELETE, "/api/v1/domains/" + id, null).status()).isEqualTo(204);

        assertThat(primaries(environmentId)).doesNotContainKey(hostname);
        assertThat(auditDetails(id, "DOMAIN_REMOVED")).contains(hostname).contains("\"isPrimary\": true");
        assertProblem(api(token, HttpMethod.DELETE, "/api/v1/domains/" + id, null), 404, "not-found");
        assertThat(add(environmentId, hostname, false).status()).as("hostname libéré").isEqualTo(201);
    }

    @Test
    void environmentOfDeletedProjectAcceptsNoDomain() {
        assertThat(api(token, HttpMethod.DELETE, "/api/v1/projects/" + projectId, null).status()).isEqualTo(204);

        assertProblem(add(environmentId, uniqueDomain(), false), 404, "not-found");
        assertProblem(add(UUID.randomUUID(), uniqueDomain(), false), 404, "not-found");
    }

    @Test
    void permissions() {
        assertProblem(api(tokenFor(RoleCode.VIEWER), HttpMethod.POST, "/api/v1/environments/" + environmentId + "/domains",
                Map.of("hostname", uniqueDomain(), "isPrimary", false)), 403, "forbidden");
        Response added = api(tokenFor(RoleCode.DEVELOPER), HttpMethod.POST, "/api/v1/environments/" + environmentId + "/domains",
                Map.of("hostname", uniqueDomain(), "isPrimary", false));
        assertThat(added.status()).isEqualTo(201);
        assertThat(api(tokenFor(RoleCode.DEVELOPER), HttpMethod.DELETE, "/api/v1/domains/" + added.json("$.id"), null).status()).isEqualTo(204);
    }
}
