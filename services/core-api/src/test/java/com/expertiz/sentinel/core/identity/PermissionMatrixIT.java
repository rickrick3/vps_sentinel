package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.identity.internal.model.PermissionMatrix;
import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.security.Permissions;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/** Matrice fixe des permissions (spec §8.12), insérée par migration et protégée en écriture par la base (spec §6.5). */
class PermissionMatrixIT extends IntegrationTest {

    // Recopiée du spec §8.12, en littéraux : la migration et les constantes Permissions sont toutes deux comparées à la spec.
    static final Map<RoleCode, Set<String>> EXPECTED = Map.of(
            RoleCode.SUPER_ADMIN, Set.of("server:read", "server:write", "server:delete", "project:read", "project:write",
                    "project:delete", "user:read", "user:manage", "audit:read"),
            RoleCode.INFRA_ADMIN, Set.of("server:read", "server:write", "server:delete", "project:read", "project:write",
                    "project:delete", "user:read", "audit:read"),
            RoleCode.DEVOPS, Set.of("server:read", "server:write", "project:read", "project:write", "project:delete"),
            RoleCode.DEVELOPER, Set.of("server:read", "project:read", "project:write"),
            RoleCode.VIEWER, Set.of("server:read", "project:read"));

    @Autowired
    PermissionMatrix matrix;

    @ParameterizedTest
    @EnumSource(RoleCode.class)
    void matrixMatchesSpec(RoleCode role) {
        assertThat(matrix.permissionsOf(role)).containsExactlyInAnyOrderElementsOf(EXPECTED.get(role));
    }

    @Test
    void databaseHoldsExactlyTheSpecRows() {
        List<String> rows = ownerJdbc()
                .sql("""
                        SELECT r.code || '=' || p.code
                          FROM role_permissions rp
                          JOIN roles r ON r.id = rp.role_id
                          JOIN permissions p ON p.id = rp.permission_id""")
                .query(String.class)
                .list();

        assertThat(rows).containsExactlyInAnyOrderElementsOf(EXPECTED.entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(p -> e.getKey() + "=" + p))
                .toList());
    }

    @Test
    void codeConstantsMatchDatabase() {
        assertThat(ownerJdbc().sql("SELECT code FROM permissions").query(String.class).list())
                .containsExactlyInAnyOrderElementsOf(Permissions.ALL)
                .containsExactlyInAnyOrderElementsOf(EXPECTED.get(RoleCode.SUPER_ADMIN));
        assertThat(ownerJdbc().sql("SELECT code FROM roles").query(String.class).list())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(RoleCode.values()).map(Enum::name).toList());
    }

    @Test
    void matrixIsImmutable() {
        assertThatThrownBy(() -> matrix.permissionsOf(RoleCode.VIEWER).add(Permissions.USER_MANAGE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(matrix.permissionsOf(RoleCode.VIEWER)).doesNotContain(Permissions.USER_MANAGE);
    }

    @Test
    void appRoleReadsMatrix() {
        assertThat(appJdbc().sql("SELECT count(*) FROM role_permissions").query(Long.class).single()).isEqualTo(27);
    }

    @ParameterizedTest
    @MethodSource("matrixWrites")
    void appRoleCannotAlterMatrix(String table, String sql) {
        assertThatThrownBy(() -> appJdbc().sql(sql).update())
                .rootCause()
                .hasMessageContaining("permission denied for table " + table);
    }

    static Stream<Arguments> matrixWrites() {
        return Stream.of(
                Arguments.of("roles", "INSERT INTO roles (code, label) VALUES ('ROOT', 'Root')"),
                Arguments.of("roles", "UPDATE roles SET label = 'Root' WHERE code = 'VIEWER'"),
                Arguments.of("roles", "DELETE FROM roles WHERE code = 'VIEWER'"),
                Arguments.of("roles", "TRUNCATE roles CASCADE"),
                Arguments.of("permissions", "INSERT INTO permissions (code, description) VALUES ('root:all', 'Tout')"),
                Arguments.of("permissions", "UPDATE permissions SET code = 'server:read' WHERE code = 'user:manage'"),
                Arguments.of("permissions", "DELETE FROM permissions WHERE code = 'audit:read'"),
                Arguments.of("permissions", "TRUNCATE permissions CASCADE"),
                // Escalade : donner user:manage au rôle VIEWER.
                Arguments.of("role_permissions", """
                        INSERT INTO role_permissions (role_id, permission_id)
                        SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'VIEWER' AND p.code = 'user:manage'"""),
                Arguments.of("role_permissions", """
                        UPDATE role_permissions SET role_id = (SELECT id FROM roles WHERE code = 'VIEWER')
                         WHERE role_id = (SELECT id FROM roles WHERE code = 'SUPER_ADMIN')"""),
                Arguments.of("role_permissions", "DELETE FROM role_permissions"),
                Arguments.of("role_permissions", "TRUNCATE role_permissions"));
    }
}
