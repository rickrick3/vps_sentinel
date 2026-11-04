package com.expertiz.sentinel.core.shared.web;

/**
 * Groupes de validation d'une requête partagée par {@code POST} et {@code PUT} (spec §10.2) : {@code version} est
 * refusée à la création et obligatoire au remplacement.
 *
 * <p>springdoc ignore les groupes : il déclarerait obligatoire en {@code POST} un champ qui n'est exigé qu'en
 * {@code PUT}. Ces champs portent donc {@code @Schema(requiredMode = NOT_REQUIRED)} et une description de la règle.
 */
public final class ValidationGroups {

    public static final String VERSION_DESCRIPTION =
            "Version lue, pour le verrouillage optimiste : obligatoire en PUT, refusée en POST.";

    public interface Create {}

    public interface Update {}

    private ValidationGroups() {}
}
