package com.expertiz.sentinel.core.infrastructure;

/** Statut administratif d'un VPS (spec §9.3). Un VPS {@code DECOMMISSIONED} ne peut plus recevoir de déploiement. */
public enum AdminStatus {
    ACTIVE,
    MAINTENANCE,
    DECOMMISSIONED;

    /**
     * Les codes valides, pour {@code @Pattern} sur une requête. Ancré, car le contrat OpenAPI reprend ce motif et un
     * motif JSON Schema n'est pas implicitement ancré.
     */
    public static final String PATTERN = "^(ACTIVE|MAINTENANCE|DECOMMISSIONED)$";
}
