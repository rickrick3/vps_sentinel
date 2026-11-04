package com.expertiz.sentinel.core.projects.internal;

/** Nature d'un environnement (spec §9.4). */
public enum EnvironmentKind {
    PRODUCTION,
    STAGING,
    DEVELOPMENT,
    OTHER;

    /** Les codes valides, pour {@code @Pattern} sur une requête. */
    public static final String PATTERN = "PRODUCTION|STAGING|DEVELOPMENT|OTHER";
}
