package com.expertiz.sentinel.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.AuthorizationMatrixIT;
import com.expertiz.sentinel.core.identity.AuthorizationMatrixIT.EndpointCase;
import com.expertiz.sentinel.core.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Contrat OpenAPI (spec §10, §13.6) : le document servi par springdoc est identique au fichier commité
 * {@code contracts/openapi/core-api.yaml}, dont le frontend génère ses types. Avec {@code UPDATE_OPENAPI=true}, le test
 * réécrit le fichier au lieu de le comparer : c'est ce que fait {@code scripts/export-openapi.sh}. Le contenu est aussi
 * vérifié : il décrit exactement les endpoints de la matrice d'autorisation, et seuls les endpoints de session se passent
 * de jeton.
 */
class OpenApiContractIT extends IntegrationTest {

    /** Depuis le répertoire du module, d'où Maven lance les tests. */
    private static final Path CONTRACT = Path.of("../../contracts/openapi/core-api.yaml");

    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    /** Statuts de succès autres que 200 ; tous les autres endpoints répondent 200. */
    private static final Map<String, String> SUCCESS = Map.ofEntries(
            Map.entry("POST /api/v1/auth/logout", "204"),
            Map.entry("POST /api/v1/users", "201"),
            Map.entry("DELETE /api/v1/users/{id}", "204"),
            Map.entry("POST /api/v1/servers", "201"),
            Map.entry("DELETE /api/v1/servers/{id}", "204"),
            Map.entry("POST /api/v1/projects", "201"),
            Map.entry("DELETE /api/v1/projects/{id}", "204"),
            Map.entry("POST /api/v1/projects/{projectId}/environments", "201"),
            Map.entry("DELETE /api/v1/environments/{id}", "204"),
            Map.entry("POST /api/v1/environments/{environmentId}/domains", "201"),
            Map.entry("DELETE /api/v1/domains/{id}", "204"),
            Map.entry("POST /api/v1/environments/{environmentId}/deployments", "201"),
            Map.entry("DELETE /api/v1/deployments/{id}", "204"));

    @Test
    void contractUpToDate() throws IOException {
        String served = get("/v3/api-docs.yaml");
        if ("true".equals(System.getenv("UPDATE_OPENAPI"))) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, served);
        }

        assertThat(CONTRACT).as("contrat absent : lancer scripts/export-openapi.sh").exists();
        assertThat(served).as("contrat périmé : lancer scripts/export-openapi.sh, puis relire le diff")
                .isEqualTo(Files.readString(CONTRACT));
    }

    @Test
    void describesTheApiAndItsBearerScheme() {
        String document = get("/v3/api-docs");

        assertThat((String) JsonPath.read(document, "$.info.title")).isEqualTo("VPS Sentinel Core API");
        assertThat((String) JsonPath.read(document, "$.info.version")).isEqualTo("1");
        assertThat((Map<String, Object>) JsonPath.read(document, "$.components.securitySchemes.bearer"))
                .containsOnly(Map.entry("type", "http"), Map.entry("scheme", "bearer"), Map.entry("bearerFormat", "JWT"));
        assertThat((List<Object>) JsonPath.read(document, "$.security")).containsExactly(Map.of("bearer", List.of()));
    }

    @Test
    void operationsAreExactlyTheMatrixEndpoints() {
        List<String> operations = new ArrayList<>();
        paths(get("/v3/api-docs")).forEach((path, item) -> item.keySet().stream()
                .filter(HTTP_METHODS::contains)
                .forEach(method -> operations.add(method.toUpperCase(Locale.ROOT) + " " + path)));

        assertThat(operations).containsExactlyInAnyOrderElementsOf(AuthorizationMatrixIT.CASES_AS_KEYS);
    }

    /** Sécurité globale {@code bearer} ; les endpoints publics la retirent ({@code security: []}), aucun autre n'y touche. */
    @Test
    void onlySessionEndpointsNeedNoToken() {
        Map<String, Map<String, Object>> paths = paths(get("/v3/api-docs"));

        for (EndpointCase c : AuthorizationMatrixIT.CASES) {
            Map<?, ?> operation = (Map<?, ?>) paths.get(c.path()).get(c.method().name().toLowerCase(Locale.ROOT));
            if (c.access().equals(AuthorizationMatrixIT.PUBLIC)) {
                assertThat(operation.get("security")).as(c.key()).isEqualTo(List.of());
            } else {
                assertThat(operation.containsKey("security")).as(c.key()).isFalse();
            }
        }
    }

    /** Un seul statut de succès par opération, celui que renvoie le contrôleur ; toute erreur est un Problem Details. */
    @Test
    void responsesAreTheSuccessStatusAndProblemDetails() {
        Map<String, Map<String, Object>> paths = paths(get("/v3/api-docs"));

        for (EndpointCase c : AuthorizationMatrixIT.CASES) {
            Map<?, ?> operation = (Map<?, ?>) paths.get(c.path()).get(c.method().name().toLowerCase(Locale.ROOT));
            Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
            assertThat(responses.keySet()).as(c.key()).containsExactlyInAnyOrder(SUCCESS.getOrDefault(c.key(), "200"), "default");
            assertThat(JsonPath.<String>read(responses, "$.default.content['application/problem+json'].schema.$ref"))
                    .as(c.key()).isEqualTo("#/components/schemas/Problem");
        }
    }

    /** Une référence sans schéma ferait échouer la génération des types du frontend. */
    @Test
    void everyReferenceResolves() {
        String document = get("/v3/api-docs");
        Map<String, Object> schemas = JsonPath.read(document, "$.components.schemas");
        List<String> references = JsonPath.read(document, "$..['$ref']");

        assertThat(references).isNotEmpty()
                .allSatisfy(reference -> assertThat(schemas).containsKey(reference.replace("#/components/schemas/", "")));
        assertThat(JsonPath.<Map<String, Object>>read(document, "$.components.schemas.Problem.properties"))
                .containsOnlyKeys("type", "title", "status", "detail", "instance", "correlationId", "errors");
    }

    /** Identifiants dérivés des noms : un suffixe numérique de springdoc signalerait une collision, résolue selon l'ordre de découverte. */
    @Test
    void operationIdsAreStableAndUnique() {
        List<String> ids = new ArrayList<>();
        paths(get("/v3/api-docs")).values().forEach(item -> item.forEach((method, operation) -> {
            if (HTTP_METHODS.contains(method)) {
                ids.add((String) ((Map<?, ?>) operation).get("operationId"));
            }
        }));

        assertThat(ids).doesNotHaveDuplicates().allMatch(id -> id.matches("[a-z][A-Za-z]+"), "lettres seulement");
    }

    private static Map<String, Map<String, Object>> paths(String document) {
        return JsonPath.read(document, "$.paths");
    }

    /** Corps lu en UTF-8 : le YAML est servi sans jeu de caractères, que le client décoderait sinon en ISO-8859-1. */
    private String get(String path) {
        byte[] body = http.get().uri(path).exchange().expectStatus().isOk().expectBody(byte[].class).returnResult().getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }
}
