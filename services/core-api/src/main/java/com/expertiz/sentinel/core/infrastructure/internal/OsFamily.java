package com.expertiz.sentinel.core.infrastructure.internal;

/** Famille de système d'exploitation d'un VPS (spec §9.3). */
public enum OsFamily {
    UBUNTU,
    DEBIAN,
    RHEL,
    ROCKY,
    ALMALINUX,
    CENTOS,
    OTHER;

    /**
     * Les codes valides, pour {@code @Pattern} sur une requête. Ancré, car le contrat OpenAPI reprend ce motif et un
     * motif JSON Schema n'est pas implicitement ancré.
     */
    public static final String PATTERN = "^(UBUNTU|DEBIAN|RHEL|ROCKY|ALMALINUX|CENTOS|OTHER)$";
}
