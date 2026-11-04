package com.expertiz.sentinel.core.shared.security;

import java.util.Set;

/**
 * Codes des permissions de la matrice fixe (spec §8.12), à utiliser dans les {@code @PreAuthorize}. La matrice
 * elle-même est en base, insérée par migration ; PermissionMatrixIT vérifie que ces constantes y correspondent.
 */
public final class Permissions {

    public static final String SERVER_READ = "server:read";
    public static final String SERVER_WRITE = "server:write";
    public static final String SERVER_DELETE = "server:delete";
    public static final String PROJECT_READ = "project:read";
    public static final String PROJECT_WRITE = "project:write";
    public static final String PROJECT_DELETE = "project:delete";
    public static final String USER_READ = "user:read";
    public static final String USER_MANAGE = "user:manage";
    public static final String AUDIT_READ = "audit:read";

    public static final Set<String> ALL = Set.of(SERVER_READ, SERVER_WRITE, SERVER_DELETE, PROJECT_READ, PROJECT_WRITE,
            PROJECT_DELETE, USER_READ, USER_MANAGE, AUDIT_READ);

    private Permissions() {}
}
