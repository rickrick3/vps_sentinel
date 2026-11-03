package com.expertiz.sentinel.core.identity.internal.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Matrice des permissions (spec §8.12), lue en base une fois au démarrage. Elle est fixe : insérée par migration et
 * protégée en écriture au niveau de la base (spec §6.5), elle ne peut pas changer pendant que l'API tourne.
 */
@Component
public class PermissionMatrix {

    private final Map<RoleCode, Set<String>> permissions;

    PermissionMatrix(JdbcClient jdbc) {
        Map<RoleCode, Set<String>> loaded = new EnumMap<>(RoleCode.class);
        for (RoleCode role : RoleCode.values()) {
            loaded.put(role, new TreeSet<>());
        }
        jdbc.sql("""
                        SELECT r.code AS role, p.code AS permission
                          FROM role_permissions rp
                          JOIN roles r ON r.id = rp.role_id
                          JOIN permissions p ON p.id = rp.permission_id""")
                .query((rs, row) -> Map.entry(RoleCode.valueOf(rs.getString("role")), rs.getString("permission")))
                .list()
                .forEach(entry -> loaded.get(entry.getKey()).add(entry.getValue()));
        loaded.replaceAll((role, codes) -> Collections.unmodifiableSet(codes));
        this.permissions = Collections.unmodifiableMap(loaded);
    }

    /** Permissions du rôle, triées par code. */
    public Set<String> permissionsOf(RoleCode role) {
        return permissions.get(role);
    }
}
