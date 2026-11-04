package com.expertiz.sentinel.core.projects;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;

/** Projets (spec §9.4, §10.1 à §10.3) : création, liste avec nombre d'environnements, remplacement, suppression en cascade. */
class ProjectsIT extends ProjectsTestBase {

    @Test
    void createGetListWithEnvironmentCount() {
        String name = Fixtures.uniqueName("Demo");
        Response created = api(token, HttpMethod.POST, "/api/v1/projects", project("  " + name + " "));
        UUID id = created(created);
        assertThat(created.headers().getLocation()).hasToString("/api/v1/projects/" + id);
        createEnvironment(id, "prod");
        createEnvironment(id, "staging");

        Response detail = getProject(id);
        assertThat((String) detail.json("$.name")).isEqualTo(name);
        assertThat((String) detail.json("$.repositoryUrl")).isEqualTo("https://github.com/expertiz/demo");
        assertThat((String) detail.json("$.description")).isEqualTo("Application de démonstration\nsur deux lignes");
        assertThat((List<String>) detail.json("$.environments[*].name")).containsExactly("prod", "staging");

        Response list = api(token, HttpMethod.GET, "/api/v1/projects?q=" + name.toUpperCase(), null);
        assertThat((List<String>) list.json("$.items[*].id")).containsExactly(id.toString());
        assertThat((Integer) list.json("$.items[0].environmentCount")).isEqualTo(2);
    }

    @Test
    void listSortsAndPaginates() {
        String marker = Fixtures.uniqueName("Zproj");
        createProject(marker + "-b");
        createProject(marker + "-a");
        createProject(marker + "-c");

        Response page = api(token, HttpMethod.GET, "/api/v1/projects?q=" + marker + "&sort=name,desc&size=2", null);

        assertThat((List<String>) page.json("$.items[*].name")).containsExactly(marker + "-c", marker + "-b");
        assertThat((Integer) page.json("$.totalItems")).isEqualTo(3);
        assertThat((List<String>) api(token, HttpMethod.GET, "/api/v1/projects?sort=description", null).json("$.errors[*].field"))
                .containsExactly("sort");
    }

    @Test
    void duplicateNameIs409() {
        String name = Fixtures.uniqueName("Demo");
        createProject(name);

        Response duplicate = api(token, HttpMethod.POST, "/api/v1/projects", project(" " + name.toLowerCase() + " "));

        assertProblem(duplicate, 409, "duplicate");
        assertThat((List<String>) duplicate.json("$.errors[*].field")).containsExactly("name");
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of("name", null), Arguments.of("name", "  "), Arguments.of("name", "p".repeat(101)),
                Arguments.of("name", "Demo\u0007"),
                Arguments.of("description", "d".repeat(2001)), Arguments.of("description", "ligne\u0000"),
                Arguments.of("repositoryUrl", "javascript:alert(1)"), Arguments.of("repositoryUrl", "https://u:p@h/x"),
                Arguments.of("repositoryUrl", "ftp://h/x"), Arguments.of("repositoryUrl", "github.com/x"),
                Arguments.of("repositoryUrl", "https://h/" + "x".repeat(500)));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidPayloads")
    void validationTable(String field, Object value) {
        Map<String, Object> body = project(Fixtures.uniqueName("Demo"));
        body.put(field, value);

        Response response = api(token, HttpMethod.POST, "/api/v1/projects", body);

        assertProblem(response, 400, "validation-error");
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactly(field);
    }

    @Test
    void putReplacesClearsOptionalAndStaleVersionIs409() {
        UUID id = createProject(Fixtures.uniqueName("Demo"));
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("name", Fixtures.uniqueName("Renamed"));
        replacement.put("version", 0);

        Response replaced = api(token, HttpMethod.PUT, "/api/v1/projects/" + id, replacement);

        assertThat(replaced.status()).as(replaced.body()).isEqualTo(200);
        assertThat((Object) replaced.json("$.description")).isNull();
        assertThat((Object) replaced.json("$.repositoryUrl")).isNull();
        assertThat((Integer) replaced.json("$.version")).isEqualTo(1);
        assertProblem(api(token, HttpMethod.PUT, "/api/v1/projects/" + id, replacement), 409, "version-conflict");
        assertThat(auditDetails(id, "PROJECT_UPDATED")).contains("\"changes\"").contains("\"repositoryUrl\"").contains("\"description\"");
    }

    @Test
    void deleteSoftDeletesProjectAndEnvironmentsWithSnapshots() {
        String name = Fixtures.uniqueName("Demo");
        UUID id = createProject(name);
        UUID prod = createEnvironment(id, "prod");
        UUID staging = createEnvironment(id, "staging");

        assertThat(api(token, HttpMethod.DELETE, "/api/v1/projects/" + id, null).status()).isEqualTo(204);

        assertProblem(getProject(id), 404, "not-found");
        assertThat(ownerJdbc().sql("SELECT count(*) FROM environments WHERE project_id = :id AND deleted_at IS NOT NULL")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(auditDetails(id, "PROJECT_DELETED")).contains(name).contains("github.com/expertiz/demo");
        assertThat(auditDetails(prod, "ENVIRONMENT_DELETED")).contains("\"name\": \"prod\"").contains(id.toString());
        assertThat(auditDetails(staging, "ENVIRONMENT_DELETED")).contains("\"name\": \"staging\"");
        assertThat(api(token, HttpMethod.POST, "/api/v1/projects", project(name)).status()).as("nom libéré").isEqualTo(201);
    }

    @Test
    void unknownProjectIs404() {
        Map<String, Object> body = project(Fixtures.uniqueName("Demo"));
        body.put("version", 0);

        assertProblem(getProject(UUID.randomUUID()), 404, "not-found");
        assertProblem(api(token, HttpMethod.PUT, "/api/v1/projects/" + UUID.randomUUID(), body), 404, "not-found");
        assertProblem(api(token, HttpMethod.DELETE, "/api/v1/projects/" + UUID.randomUUID(), null), 404, "not-found");
    }

    @Test
    void permissions() {
        UUID id = createProject(Fixtures.uniqueName("Demo"));

        assertThat(api(tokenFor(RoleCode.VIEWER), HttpMethod.GET, "/api/v1/projects/" + id, null).status()).isEqualTo(200);
        assertProblem(api(tokenFor(RoleCode.VIEWER), HttpMethod.POST, "/api/v1/projects", project(Fixtures.uniqueName("D"))), 403, "forbidden");
        assertThat(api(tokenFor(RoleCode.DEVELOPER), HttpMethod.POST, "/api/v1/projects", project(Fixtures.uniqueName("D"))).status())
                .isEqualTo(201);
        assertProblem(api(tokenFor(RoleCode.DEVELOPER), HttpMethod.DELETE, "/api/v1/projects/" + id, null), 403, "forbidden");
        assertThat(api(token, HttpMethod.DELETE, "/api/v1/projects/" + id, null).status()).isEqualTo(204);
    }
}
