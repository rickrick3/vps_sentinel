package com.expertiz.sentinel.core.identity.internal.model;

/** Rôles fixes de la phase 1 (spec §8.12), un seul par utilisateur. */
public enum RoleCode {
    SUPER_ADMIN,
    INFRA_ADMIN,
    DEVOPS,
    DEVELOPER,
    VIEWER;

    /** Les codes valides, pour {@code @Pattern} sur une requête : une valeur inconnue est une erreur de champ. */
    public static final String PATTERN = "SUPER_ADMIN|INFRA_ADMIN|DEVOPS|DEVELOPER|VIEWER";
}
