package com.expertiz.sentinel.core.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;

/**
 * Inventaire des VPS (spec §9.3, §10.1 à §10.3) : création, remplacement complet par {@code PUT}, verrouillage optimiste,
 * filtres et facettes, suppression logique, validation, permissions et audit.
 */
class ServersIT extends IntegrationTest {

    String token;

    @BeforeEach
    void infraAdmin() {
        token = tokenFor(RoleCode.INFRA_ADMIN);
    }

    static Map<String, Object> server(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("hostname", "Web-01.Example.COM");
        body.put("publicIp", "203.0.113.10");
        body.put("privateIp", "2001:db8:0:0:0:0:0:1");
        body.put("provider", "OVH");
        body.put("region", "gra");
        body.put("osFamily", "UBUNTU");
        body.put("osVersion", "24.04");
        body.put("cpuCores", 4);
        body.put("ramMb", 8192);
        body.put("diskGb", 160);
        body.put("adminStatus", "ACTIVE");
        body.put("description", "Serveur web\nfront");
        body.put("tags", List.of(" Prod ", "web", "PROD"));
        return body;
    }

    private Response create(Map<String, Object> body) {
        return api(token, HttpMethod.POST, "/api/v1/servers", body);
    }

    private UUID createId(Map<String, Object> body) {
        Response created = create(body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        return UUID.fromString(created.json("$.id"));
    }

    private Response get(UUID id) {
        return api(token, HttpMethod.GET, "/api/v1/servers/" + id, null);
    }

    private Response put(UUID id, Map<String, Object> body) {
        return api(token, HttpMethod.PUT, "/api/v1/servers/" + id, body);
    }

    private Response list(String query) {
        return api(token, HttpMethod.GET, "/api/v1/servers?" + query, null);
    }

    private static void assertProblem(Response response, int status, String type) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:" + type);
    }

    @Test
    void createAndGet() {
        String name = Fixtures.uniqueName("VPS");

        Response created = create(server("  " + name + " "));

        assertThat(created.status()).isEqualTo(201);
        UUID id = UUID.fromString(created.json("$.id"));
        assertThat(created.headers().getLocation()).hasToString("/api/v1/servers/" + id);
        Response read = get(id);
        assertThat(read.status()).isEqualTo(200);
        assertThat((String) read.json("$.name")).isEqualTo(name);
        assertThat((String) read.json("$.hostname")).isEqualTo("web-01.example.com");
        assertThat((String) read.json("$.publicIp")).isEqualTo("203.0.113.10");
        assertThat((String) read.json("$.privateIp")).isEqualTo("2001:db8::1");
        assertThat((String) read.json("$.osFamily")).isEqualTo("UBUNTU");
        assertThat((Integer) read.json("$.ramMb")).isEqualTo(8192);
        assertThat((String) read.json("$.adminStatus")).isEqualTo("ACTIVE");
        assertThat((String) read.json("$.description")).isEqualTo("Serveur web\nfront");
        assertThat((List<String>) read.json("$.tags")).containsExactly("prod", "web");
        assertThat((Integer) read.json("$.version")).isZero();
    }

    @Test
    void adminStatusDefaultsToActiveOnCreate() {
        Map<String, Object> body = server(Fixtures.uniqueName("VPS"));
        body.remove("adminStatus");

        assertThat((String) create(body).json("$.adminStatus")).isEqualTo("ACTIVE");
    }

    @Test
    void duplicateNameCaseAndSpaceInsensitive() {
        // Review Focus 1 : « VPS-01 » puis «  vps-01  ».
        String name = Fixtures.uniqueName("VPS");
        create(server(name.toUpperCase()));

        Response duplicate = create(server("  " + name.toLowerCase() + "  "));

        assertProblem(duplicate, 409, "duplicate");
        assertThat((List<String>) duplicate.json("$.errors[*].field")).containsExactly("name");
    }

    @Test
    void putReplacesAndClearsOptional() {
        UUID id = createId(server(Fixtures.uniqueName("VPS")));
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("name", Fixtures.uniqueName("VPS"));
        replacement.put("adminStatus", "MAINTENANCE");
        replacement.put("version", 0);
        replacement.put("hostname", null);

        Response response = put(id, replacement);

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        Response read = get(id);
        assertThat((String) read.json("$.adminStatus")).isEqualTo("MAINTENANCE");
        assertThat((Object) read.json("$.hostname")).isNull();
        assertThat((Object) read.json("$.publicIp")).isNull();
        assertThat((Object) read.json("$.cpuCores")).isNull();
        assertThat((List<String>) read.json("$.tags")).isEmpty();
        assertThat((Integer) read.json("$.version")).isEqualTo(1);
    }

    @Test
    void putStaleVersionIs409() {
        UUID id = createId(server(Fixtures.uniqueName("VPS")));
        Map<String, Object> first = server(Fixtures.uniqueName("VPS"));
        first.put("version", 0);
        assertThat(put(id, first).status()).isEqualTo(200);

        Map<String, Object> stale = server(Fixtures.uniqueName("VPS"));
        stale.put("version", 0);
        assertProblem(put(id, stale), 409, "version-conflict");
    }

    @Test
    void putRequiresVersionAndAdminStatus() {
        UUID id = createId(server(Fixtures.uniqueName("VPS")));
        Map<String, Object> body = server(Fixtures.uniqueName("VPS"));
        body.remove("adminStatus");

        Response response = put(id, body);

        assertProblem(response, 400, "validation-error");
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactlyInAnyOrder("version", "adminStatus");
    }

    @Test
    void versionRefusedOnCreate() {
        Map<String, Object> body = server(Fixtures.uniqueName("VPS"));
        body.put("version", 3);

        assertThat((List<String>) create(body).json("$.errors[*].field")).containsExactly("version");
    }

    @Test
    void unknownServerIs404() {
        Map<String, Object> body = server(Fixtures.uniqueName("VPS"));
        body.put("version", 0);

        assertProblem(get(UUID.randomUUID()), 404, "not-found");
        assertProblem(put(UUID.randomUUID(), body), 404, "not-found");
        assertProblem(api(token, HttpMethod.DELETE, "/api/v1/servers/" + UUID.randomUUID(), null), 404, "not-found");
    }

    @Test
    void pageBeyondLastIsEmpty() {
        String marker = Fixtures.uniqueName("Zpage");
        createId(server(marker + "-a"));
        createId(server(marker + "-b"));

        Response response = list("q=" + marker + "&page=999&size=10");

        assertThat(response.status()).isEqualTo(200);
        assertThat((List<Object>) response.json("$.items")).isEmpty();
        assertThat((Integer) response.json("$.totalItems")).isEqualTo(2);
        assertThat((Integer) response.json("$.page")).isEqualTo(999);
    }

    @Test
    void filters() {
        String marker = Fixtures.uniqueName("Zflt").toLowerCase();
        Map<String, Object> a = server(marker + "-a");
        a.put("hostname", marker + "-host.example.com");
        a.put("publicIp", "198.51.100.77");
        a.put("provider", "Hetzner-" + marker);
        a.put("region", "fsn1-" + marker);
        a.put("tags", List.of("db-" + marker));
        Map<String, Object> b = server(marker + "-b");
        b.put("hostname", null);
        b.put("privateIp", "2001:db8::" + Integer.toHexString(Math.abs(marker.hashCode() % 0xffff)));
        b.put("provider", "OVH-" + marker);
        b.put("adminStatus", "MAINTENANCE");
        UUID idA = createId(a);
        UUID idB = createId(b);

        assertThat(ids(list("q=" + marker + "-HOST"))).containsExactly(idA.toString());
        assertThat(ids(list("q=198.51.100.77"))).contains(idA.toString()).doesNotContain(idB.toString());
        assertThat(ids(list("q=" + b.get("privateIp")))).containsExactly(idB.toString());
        assertThat(ids(list("q=" + marker + "&tag=DB-" + marker))).containsExactly(idA.toString());
        assertThat(ids(list("q=" + marker + "&provider=hetzner-" + marker))).containsExactly(idA.toString());
        assertThat(ids(list("q=" + marker + "&region=FSN1-" + marker))).containsExactly(idA.toString());
        assertThat(ids(list("q=" + marker + "&adminStatus=MAINTENANCE"))).containsExactly(idB.toString());
        assertThat(ids(list("q=" + marker + "&sort=name,desc"))).containsExactly(idB.toString(), idA.toString());
    }

    @Test
    void invalidListParametersAre400() {
        assertThat((List<String>) list("sort=description,asc").json("$.errors[*].field")).containsExactly("sort");
        assertThat((List<String>) list("adminStatus=BROKEN").json("$.errors[*].field")).containsExactly("adminStatus");
        assertThat((List<String>) list("size=0").json("$.errors[*].field")).containsExactly("size");
    }

    @Test
    void facetsDistinctSortedExcludingDeleted() {
        String marker = Fixtures.uniqueName("zfacet").toLowerCase();
        Map<String, Object> one = server(Fixtures.uniqueName("VPS"));
        one.put("provider", marker + "-b");
        one.put("region", marker + "-r1");
        one.put("tags", List.of(marker + "-t2", marker + "-t1"));
        Map<String, Object> two = server(Fixtures.uniqueName("VPS"));
        two.put("provider", marker + "-a");
        two.put("region", marker + "-r1");
        two.put("tags", List.of(marker + "-t1"));
        Map<String, Object> gone = server(Fixtures.uniqueName("VPS"));
        gone.put("provider", marker + "-c");
        gone.put("tags", List.of(marker + "-t3"));
        createId(one);
        createId(two);
        UUID goneId = createId(gone);
        assertThat(api(token, HttpMethod.DELETE, "/api/v1/servers/" + goneId, null).status()).isEqualTo(204);

        Response facets = api(token, HttpMethod.GET, "/api/v1/servers/facets", null);

        assertThat(facets.status()).isEqualTo(200);
        assertThat(ofMarker(facets.json("$.providers"), marker)).containsExactly(marker + "-a", marker + "-b");
        assertThat(ofMarker(facets.json("$.regions"), marker)).containsExactly(marker + "-r1");
        assertThat(ofMarker(facets.json("$.tags"), marker)).containsExactly(marker + "-t1", marker + "-t2");
        List<String> providers = facets.json("$.providers");
        assertThat(providers).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void deleteSoftDeletesAndFreesName() {
        String name = Fixtures.uniqueName("VPS");
        UUID id = createId(server(name));

        assertThat(api(token, HttpMethod.DELETE, "/api/v1/servers/" + id, null).status()).isEqualTo(204);

        assertProblem(get(id), 404, "not-found");
        assertThat(ownerJdbc().sql("SELECT deleted_at IS NOT NULL FROM servers WHERE id = :id").param("id", id).query(Boolean.class).single())
                .isTrue();
        assertThat(create(server(name)).status()).isEqualTo(201);
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of("name", ""), Arguments.of("name", "-vps"), Arguments.of("name", "vps@01"),
                Arguments.of("name", "v".repeat(101)), Arguments.of("name", null),
                Arguments.of("hostname", "-bad.com"), Arguments.of("hostname", "bad..com"), Arguments.of("hostname", "a_b.com"),
                Arguments.of("hostname", "a".repeat(64) + ".com"),
                Arguments.of("publicIp", "999.1.1.1"), Arguments.of("publicIp", "localhost"), Arguments.of("privateIp", "10.0.0.0/8"),
                Arguments.of("provider", "p".repeat(101)), Arguments.of("region", "r".repeat(101)),
                Arguments.of("provider", "OVH\u0007"),
                Arguments.of("osFamily", "WINDOWS"), Arguments.of("osVersion", "24.04!"), Arguments.of("osVersion", "1".repeat(33)),
                Arguments.of("cpuCores", 0), Arguments.of("cpuCores", 1025), Arguments.of("ramMb", 0), Arguments.of("ramMb", 16_777_217),
                Arguments.of("diskGb", 0), Arguments.of("diskGb", 1_048_577),
                Arguments.of("description", "d".repeat(2001)), Arguments.of("description", "ligne\u0007"),
                Arguments.of("adminStatus", "BROKEN"),
                Arguments.of("tags", List.of("a b")), Arguments.of("tags", List.of("t".repeat(51))),
                Arguments.of("tags", IntStream.range(0, 21).mapToObj(i -> "t" + i).toList()));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidPayloads")
    void validationTable(String field, Object value) {
        Map<String, Object> body = server(Fixtures.uniqueName("VPS"));
        body.put(field, value);

        Response response = create(body);

        assertProblem(response, 400, "validation-error");
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactly(field);
    }

    @Test
    void boundaryValuesAccepted() {
        Map<String, Object> body = server(Fixtures.uniqueName("V"));
        body.put("cpuCores", 1024);
        body.put("ramMb", 16_777_216);
        body.put("diskGb", 1_048_576);
        body.put("description", "d".repeat(2000));
        body.put("tags", IntStream.range(0, 20).mapToObj(i -> "t" + i).toList());

        assertThat(create(body).status()).isEqualTo(201);
    }

    @Test
    void permissions() {
        UUID id = createId(server(Fixtures.uniqueName("VPS")));
        Map<String, Object> update = server(Fixtures.uniqueName("VPS"));
        update.put("version", 0);

        assertThat(api(tokenFor(RoleCode.VIEWER), HttpMethod.GET, "/api/v1/servers/" + id, null).status()).isEqualTo(200);
        assertProblem(api(tokenFor(RoleCode.VIEWER), HttpMethod.POST, "/api/v1/servers", server(Fixtures.uniqueName("VPS"))), 403, "forbidden");
        assertProblem(api(tokenFor(RoleCode.DEVELOPER), HttpMethod.PUT, "/api/v1/servers/" + id, update), 403, "forbidden");
        assertThat(api(tokenFor(RoleCode.DEVOPS), HttpMethod.PUT, "/api/v1/servers/" + id, update).status()).isEqualTo(200);
        assertProblem(api(tokenFor(RoleCode.DEVOPS), HttpMethod.DELETE, "/api/v1/servers/" + id, null), 403, "forbidden");
        assertThat(api(token, HttpMethod.DELETE, "/api/v1/servers/" + id, null).status()).isEqualTo(204);
    }

    @Test
    void auditDiffAndSnapshot() {
        UUID id = createId(server(Fixtures.uniqueName("VPS")));
        Map<String, Object> update = server((String) get(id).json("$.name"));
        update.put("region", "rbx");
        update.put("tags", List.of("prod", "web", "edge"));
        update.put("version", 0);
        assertThat(put(id, update).status()).isEqualTo(200);
        api(token, HttpMethod.DELETE, "/api/v1/servers/" + id, null);

        String created = auditDetails(id, "SERVER_CREATED");
        assertThat(created).contains("\"region\": \"gra\"").contains("\"publicIp\": \"203.0.113.10\"");
        Map<String, Object> changes = ownerJdbc()
                .sql("SELECT details -> 'changes' AS changes FROM audit_logs WHERE target_id = :id AND action = 'SERVER_UPDATED'")
                .param("id", id)
                .query((rs, row) -> Map.<String, Object>of("changes", rs.getString("changes")))
                .single();
        assertThat((String) changes.get("changes"))
                .contains("\"region\": {\"to\": \"rbx\", \"from\": \"gra\"}")
                .contains("\"tags\"")
                .doesNotContain("\"name\"")
                .doesNotContain("\"hostname\"");
        assertThat(auditDetails(id, "SERVER_DELETED")).contains("\"region\": \"rbx\"").contains("edge");
    }

    private String auditDetails(UUID id, String action) {
        return ownerJdbc().sql("SELECT details::text FROM audit_logs WHERE target_id = :id AND action = :action")
                .param("id", id)
                .param("action", action)
                .query(String.class)
                .single();
    }

    private static List<String> ids(Response response) {
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json("$.items[*].id");
    }

    private static List<String> ofMarker(List<String> values, String marker) {
        return values.stream().filter(v -> v.startsWith(marker)).toList();
    }
}
