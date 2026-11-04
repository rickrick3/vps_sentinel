package com.expertiz.sentinel.core.shared.web;

/**
 * Groupes de validation d'une requête partagée par {@code POST} et {@code PUT} (spec §10.2) : {@code version} est
 * refusée à la création et obligatoire au remplacement.
 */
public final class ValidationGroups {

    public interface Create {}

    public interface Update {}

    private ValidationGroups() {}
}
