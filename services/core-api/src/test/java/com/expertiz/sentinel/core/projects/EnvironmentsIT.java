package com.expertiz.sentinel.core.projects;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

/** Environnements (spec §9.4, §10.3) : nom unique par projet, sans tenir compte de la casse ; introuvables avec leur projet. */
class EnvironmentsIT extends ProjectsTestBase {

    UUID projectId;

    @BeforeEach
    void project() {
        projectId = createProject(Fixtures.uniqueName("Demo"));
    }

    private Response createIn(UUID project, Map<String, Object> body) {
        return api(token, HttpMethod.POST, "/api/v1/projects/" + project + "/environments", body);
    }

    private Response put(UUID id, String name, String kind, long version) {
        Map<String, Object> body = environment(name, kind);
        body.put("version", version);
        return api(token, HttpMethod.PUT, "/api/v1/environments/" + id, body);
    }

    @Test
    void nameUniquePerProjectCaseInsensitive() {
        createEnvironment(projectId, "prod");

        Response duplicate = createIn(projectId, environment("PROD", "STAGING"));
        assertProblem(duplicate, 409, "duplicate");
        assertThat((List<String>) duplicate.json("$.errors[*].field")).containsExactly("name");

        UUID other = createProject(Fixtures.uniqueName("Other"));
        assertThat(createIn(other, environment("prod", "PRODUCTION")).status()).isEqualTo(201);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
            pré-prod          | PRODUCTION | name
            pré prod          | PRODUCTION | name
            -prod             | PRODUCTION | name
            NULL              | PRODUCTION | name
            prod              | QA         | kind
            prod              | NULL       | kind
            """)
    void environmentRules(String name, String kind, String field) {
        Response response = createIn(projectId, environment(name, kind));

        assertProblem(response, 400, "validation-error");
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactly(field);
    }

    @Test
    void nameAtMost50Characters() {
        assertThat(createIn(projectId, environment("e".repeat(50), "OTHER")).status()).isEqualTo(201);
        assertThat((List<String>) createIn(projectId, environment("f".repeat(51), "OTHER")).json("$.errors[*].field"))
                .containsExactly("name");
    }

    @Test
    void putAndDelete() {
        UUID id = createEnvironment(projectId, "staging");

        Response replaced = put(id, "recette", "STAGING", 0);

        assertThat(replaced.status()).as(replaced.body()).isEqualTo(200);
        assertThat((String) replaced.json("$.name")).isEqualTo("recette");
        assertThat((Integer) replaced.json("$.version")).isEqualTo(1);
        assertProblem(put(id, "encore", "STAGING", 0), 409, "version-conflict");
        assertThat(auditDetails(id, "ENVIRONMENT_UPDATED")).contains("\"to\": \"recette\"");

        assertThat(api(token, HttpMethod.DELETE, "/api/v1/environments/" + id, null).status()).isEqualTo(204);
        assertProblem(put(id, "recette", "STAGING", 1), 404, "not-found");
        assertThat((List<String>) getProject(projectId).json("$.environments[*].name")).isEmpty();
        assertThat(createIn(projectId, environment("recette", "STAGING")).status()).as("nom libéré").isEqualTo(201);
    }

    @Test
    void putOnEnvironmentOfDeletedProjectIs404() {
        // Review Focus 3 : modifier l'enfant d'un parent supprimé répond 404, pas 500.
        UUID id = createEnvironment(projectId, "prod");
        assertThat(api(token, HttpMethod.DELETE, "/api/v1/projects/" + projectId, null).status()).isEqualTo(204);

        assertProblem(put(id, "prod", "PRODUCTION", 0), 404, "not-found");
        assertProblem(api(token, HttpMethod.DELETE, "/api/v1/environments/" + id, null), 404, "not-found");
        assertProblem(createIn(projectId, environment("staging", "STAGING")), 404, "not-found");
    }

    @Test
    void unknownProjectOrEnvironmentIs404() {
        assertProblem(createIn(UUID.randomUUID(), environment("prod", "PRODUCTION")), 404, "not-found");
        assertProblem(put(UUID.randomUUID(), "prod", "PRODUCTION", 0), 404, "not-found");
    }

    @Test
    void permissions() {
        String developer = tokenFor(RoleCode.DEVELOPER);
        Response created = api(developer, HttpMethod.POST, "/api/v1/projects/" + projectId + "/environments", environment("dev", "DEVELOPMENT"));
        assertThat(created.status()).isEqualTo(201);
        String id = created.json("$.id");

        assertProblem(api(developer, HttpMethod.DELETE, "/api/v1/environments/" + id, null), 403, "forbidden");
        assertProblem(api(tokenFor(RoleCode.VIEWER), HttpMethod.POST, "/api/v1/projects/" + projectId + "/environments",
                environment("qa", "OTHER")), 403, "forbidden");
        assertThat(api(token, HttpMethod.DELETE, "/api/v1/environments/" + id, null).status()).isEqualTo(204);
    }
}
