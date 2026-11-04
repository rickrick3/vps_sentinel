package com.expertiz.sentinel.core.projects;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Déploiements (spec §7.2, §9.4, §9.6) : un environnement ne vise jamais un VPS supprimé ou décommissionné, un VPS
 * utilisé ne peut être ni supprimé ni décommissionné, y compris en concurrence.
 */
class DeploymentsIT extends ProjectsTestBase {

    UUID projectId;
    String projectName;
    UUID environmentId;

    @BeforeEach
    void environment() {
        projectName = Fixtures.uniqueName("Demo");
        projectId = createProject(projectName);
        environmentId = createEnvironment(projectId, "prod");
    }

    private Response attach(UUID environment, UUID server, String role) {
        Map<String, Object> body = new HashMap<>();
        body.put("serverId", server);
        body.put("role", role);
        return api(token, HttpMethod.POST, "/api/v1/environments/" + environment + "/deployments", body);
    }

    private Response deleteServer(UUID server) {
        return api(infraToken, HttpMethod.DELETE, "/api/v1/servers/" + server, null);
    }

    private Response decommission(UUID server) {
        Response current = api(infraToken, HttpMethod.GET, "/api/v1/servers/" + server, null);
        Map<String, Object> body = new HashMap<>();
        body.put("name", current.json("$.name"));
        body.put("adminStatus", "DECOMMISSIONED");
        body.put("version", current.json("$.version"));
        return api(infraToken, HttpMethod.PUT, "/api/v1/servers/" + server, body);
    }

    private long deploymentsOn(UUID server) {
        return ownerJdbc().sql("SELECT count(*) FROM deployments WHERE server_id = :s").param("s", server).query(Long.class).single();
    }

    @Test
    void attachAndListOnServer() {
        UUID server = createServer();

        Response attached = attach(environmentId, server, "  web ");

        assertThat(attached.status()).as(attached.body()).isEqualTo(201);
        assertThat((String) attached.json("$.role")).isEqualTo("web");
        Response hosted = api(tokenFor(RoleCode.VIEWER), HttpMethod.GET, "/api/v1/servers/" + server + "/deployments", null);
        assertThat(hosted.status()).isEqualTo(200);
        assertThat((List<String>) hosted.json("$[*].projectName")).containsExactly(projectName);
        assertThat((List<String>) hosted.json("$[*].environmentName")).containsExactly("prod");
        assertThat((List<String>) hosted.json("$[*].role")).containsExactly("web");
        assertThat((List<String>) hosted.json("$[*].deploymentId")).containsExactly((String) attached.json("$.id"));
        assertThat(ownerJdbc().sql("SELECT created_by IS NOT NULL FROM deployments WHERE id = :id")
                        .param("id", UUID.fromString(attached.json("$.id")))
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void unknownOrDeletedServerHasNoDeploymentList() {
        UUID server = createServer();
        deleteServer(server);

        assertProblem(api(token, HttpMethod.GET, "/api/v1/servers/" + server + "/deployments", null), 404, "not-found");
        assertProblem(api(token, HttpMethod.GET, "/api/v1/servers/" + UUID.randomUUID() + "/deployments", null), 404, "not-found");
    }

    @Test
    void duplicateAttachIs409DeploymentExists() {
        UUID server = createServer();
        attach(environmentId, server, "web");

        assertProblem(attach(environmentId, server, "db"), 409, "deployment-exists");
        assertThat(attach(createEnvironment(projectId, "staging"), server, "web").status()).as("autre environnement").isEqualTo(201);
    }

    @Test
    void deploymentRoleRules() {
        assertThat(attach(environmentId, createServer(), null).status()).isEqualTo(201);
        assertThat(attach(environmentId, createServer(), "web worker").status()).isEqualTo(201);
        assertThat((List<String>) attach(environmentId, createServer(), "-web").json("$.errors[*].field")).containsExactly("role");
        assertThat((List<String>) attach(environmentId, createServer(), "r".repeat(51)).json("$.errors[*].field")).containsExactly("role");
        assertThat((List<String>) attach(environmentId, null, "web").json("$.errors[*].field")).containsExactly("serverId");
    }

    @Test
    void decommissionedOrDeletedServerNotDeployable() {
        UUID decommissioned = createServer();
        assertThat(decommission(decommissioned).status()).isEqualTo(200);
        UUID deleted = createServer();
        assertThat(deleteServer(deleted).status()).isEqualTo(204);

        assertProblem(attach(environmentId, decommissioned, "web"), 409, "server-not-deployable");
        assertProblem(attach(environmentId, deleted, "web"), 409, "server-not-deployable");
        assertProblem(attach(environmentId, UUID.randomUUID(), "web"), 404, "not-found");
        assertProblem(attach(UUID.randomUUID(), createServer(), "web"), 404, "not-found");
    }

    @Test
    void deleteOrDecommissionServerInUseIs409() {
        UUID server = createServer();
        attach(environmentId, server, "web");

        Response delete = deleteServer(server);
        assertProblem(delete, 409, "server-in-use");
        assertThat((String) delete.json("$.detail")).isEqualTo("VPS utilisé par des déploiements");
        assertProblem(decommission(server), 409, "server-in-use");
        assertThat((String) api(infraToken, HttpMethod.GET, "/api/v1/servers/" + server, null).json("$.adminStatus")).isEqualTo("ACTIVE");
    }

    @Test
    void detachThenDeleteServer() {
        UUID server = createServer();
        String deployment = attach(environmentId, server, "web").json("$.id");

        assertThat(api(token, HttpMethod.DELETE, "/api/v1/deployments/" + deployment, null).status()).isEqualTo(204);

        assertThat(auditDetails(UUID.fromString(deployment), "DEPLOYMENT_REMOVED")).contains(server.toString()).contains("web");
        assertThat(deleteServer(server).status()).isEqualTo(204);
        assertProblem(api(token, HttpMethod.DELETE, "/api/v1/deployments/" + deployment, null), 404, "not-found");
    }

    @Test
    void deletingEnvironmentRemovesChildrenWithSnapshots() {
        UUID server = createServer();
        String hostname = Fixtures.uniqueName("d").toLowerCase() + ".example.com";
        String domain = api(token, HttpMethod.POST, "/api/v1/environments/" + environmentId + "/domains",
                Map.of("hostname", hostname, "isPrimary", true)).json("$.id");
        String deployment = attach(environmentId, server, "web").json("$.id");

        assertThat(api(token, HttpMethod.DELETE, "/api/v1/environments/" + environmentId, null).status()).isEqualTo(204);

        assertThat(auditDetails(UUID.fromString(domain), "DOMAIN_REMOVED")).contains("\"hostname\": \"" + hostname + "\"");
        assertThat(auditDetails(UUID.fromString(deployment), "DEPLOYMENT_REMOVED")).contains("\"serverId\": \"" + server + "\"");
        assertThat(deploymentsOn(server)).isZero();
        assertThat(deleteServer(server).status()).as("VPS libéré").isEqualTo(204);
    }

    @Test
    void deletingProjectFreesItsServers() {
        UUID server = createServer();
        attach(environmentId, server, "web");

        assertThat(api(token, HttpMethod.DELETE, "/api/v1/projects/" + projectId, null).status()).isEqualTo(204);

        assertThat(deploymentsOn(server)).isZero();
        assertThat(deleteServer(server).status()).isEqualTo(204);
    }

    @Test
    void projectDetailIncludesDomainsAndDeploymentsWithServerStatus() {
        UUID server = createServer();
        String serverName = api(infraToken, HttpMethod.GET, "/api/v1/servers/" + server, null).json("$.name");
        String hostname = Fixtures.uniqueName("d").toLowerCase() + ".example.com";
        api(token, HttpMethod.POST, "/api/v1/environments/" + environmentId + "/domains", Map.of("hostname", hostname, "isPrimary", true));
        attach(environmentId, server, "web");

        Response detail = getProject(projectId);

        assertThat((List<String>) detail.json("$.environments[0].domains[*].hostname")).containsExactly(hostname);
        assertThat((List<String>) detail.json("$.environments[0].deployments[*].serverName")).containsExactly(serverName);
        assertThat((List<String>) detail.json("$.environments[0].deployments[*].serverAdminStatus")).containsExactly("ACTIVE");
        assertThat((List<String>) detail.json("$.environments[0].deployments[*].role")).containsExactly("web");
    }

    @Test
    void permissions() {
        UUID server = createServer();
        assertProblem(api(tokenFor(RoleCode.VIEWER), HttpMethod.POST, "/api/v1/environments/" + environmentId + "/deployments",
                Map.of("serverId", server)), 403, "forbidden");
        String developer = tokenFor(RoleCode.DEVELOPER);
        Response attached = api(developer, HttpMethod.POST, "/api/v1/environments/" + environmentId + "/deployments", Map.of("serverId", server));
        assertThat(attached.status()).isEqualTo(201);
        assertThat(api(developer, HttpMethod.DELETE, "/api/v1/deployments/" + attached.json("$.id"), null).status()).isEqualTo(204);
    }

    @Test
    void concurrentAttachAndDeleteNeverOrphans() throws Exception {
        for (int iteration = 0; iteration < 30; iteration++) {
            UUID server = createServer();
            int[] statuses = race(() -> attach(environmentId, server, "web").status(), () -> deleteServer(server).status());

            boolean deleted = ownerJdbc().sql("SELECT deleted_at IS NOT NULL FROM servers WHERE id = :s").param("s", server)
                    .query(Boolean.class).single();
            assertThat(deleted && deploymentsOn(server) > 0).as("itération %d : statuts %s", iteration, List.of(statuses[0], statuses[1]))
                    .isFalse();
            assertThat(List.of(statuses[0], statuses[1])).as("itération %d", iteration).isIn(List.of(201, 409), List.of(409, 204));
        }
    }

    @Test
    void concurrentAttachAndEnvironmentDeleteNeverOrphans() throws Exception {
        // Sans verrou sur l'environnement, un déploiement validé après sa suppression resterait invisible et rendrait le
        // VPS impossible à supprimer.
        for (int iteration = 0; iteration < 20; iteration++) {
            UUID environment = createEnvironment(projectId, "race" + iteration);
            UUID server = createServer();
            int[] statuses = race(() -> attach(environment, server, "web").status(),
                    () -> api(token, HttpMethod.DELETE, "/api/v1/environments/" + environment, null).status());

            assertThat(statuses[1]).as("itération %d", iteration).isEqualTo(204);
            assertThat(statuses[0]).as("itération %d", iteration).isIn(201, 404);
            assertThat(deploymentsOn(server)).as("itération %d", iteration).isZero();
            assertThat(deleteServer(server).status()).as("itération %d", iteration).isEqualTo(204);
        }
    }

    private static int[] race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(() -> {
                start.await();
                return first.call();
            });
            Future<Integer> b = pool.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            return new int[] {a.get(), b.get()};
        } finally {
            pool.shutdownNow();
        }
    }
}
