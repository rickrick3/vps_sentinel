package com.expertiz.sentinel.core.projects.internal;

/** Nature d'un environnement (spec §9.4). */
public enum EnvironmentKind {
    PRODUCTION,
    STAGING,
    DEVELOPMENT,
    OTHER;

    /**
     * Les codes valides, pour {@code @Pattern} sur une requête. Ancré, car le contrat OpenAPI reprend ce motif et un
     * motif JSON Schema n'est pas implicitement ancré.
     */
    public static final String PATTERN = "^(PRODUCTION|STAGING|DEVELOPMENT|OTHER)$";
}
