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

    /** Les codes valides, pour {@code @Pattern} sur une requête. */
    public static final String PATTERN = "UBUNTU|DEBIAN|RHEL|ROCKY|ALMALINUX|CENTOS|OTHER";
}
