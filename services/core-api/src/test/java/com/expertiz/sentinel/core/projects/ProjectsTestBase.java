package com.expertiz.sentinel.core.projects;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;

/** Données et appels communs aux tests du module {@code projects} : un DEVOPS écrit, un INFRA_ADMIN gère les VPS. */
public abstract class ProjectsTestBase extends IntegrationTest {

    protected String token;
    protected String infraToken;

    @BeforeEach
    void tokens() {
        token = tokenFor(RoleCode.DEVOPS);
        infraToken = tokenFor(RoleCode.INFRA_ADMIN);
    }

    protected static Map<String, Object> project(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "Application de démonstration\nsur deux lignes");
        body.put("repositoryUrl", "https://github.com/expertiz/demo");
        return body;
    }

    protected static Map<String, Object> environment(String name, String kind) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("kind", kind);
        return body;
    }

    protected UUID createProject(String name) {
        return created(api(token, HttpMethod.POST, "/api/v1/projects", project(name)));
    }

    protected UUID createEnvironment(UUID projectId, String name) {
        return created(api(token, HttpMethod.POST, "/api/v1/projects/" + projectId + "/environments", environment(name, "PRODUCTION")));
    }

    protected UUID createServer() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", Fixtures.uniqueName("VPS"));
        return created(api(infraToken, HttpMethod.POST, "/api/v1/servers", body));
    }

    protected Response getProject(UUID id) {
        return api(token, HttpMethod.GET, "/api/v1/projects/" + id, null);
    }

    protected static UUID created(Response response) {
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return UUID.fromString(response.json("$.id"));
    }

    protected static void assertProblem(Response response, int status, String type) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:" + type);
    }

    protected String auditDetails(UUID target, String action) {
        return ownerJdbc().sql("SELECT details::text FROM audit_logs WHERE target_id = :id AND action = :action")
                .param("id", target)
                .param("action", action)
                .query(String.class)
                .single();
    }
}
