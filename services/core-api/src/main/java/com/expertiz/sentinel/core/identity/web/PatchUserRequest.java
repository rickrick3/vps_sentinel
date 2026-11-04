package com.expertiz.sentinel.core.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Forme documentée de {@code PATCH /api/v1/users/{id}} pour le contrat OpenAPI. Le contrôleur lit le JSON brut
 * ({@code PatchUserCommand}) : seul ce moyen distingue un champ absent d'un {@code null} explicite.
 */
@Schema(description = "Champs absents inchangés ; null refusé ; champ inconnu refusé.")
public record PatchUserRequest(
        @Schema(maxLength = 120) String fullName,
        @Schema(allowableValues = {"SUPER_ADMIN", "INFRA_ADMIN", "DEVOPS", "DEVELOPER", "VIEWER"}) String role,
        @Schema(allowableValues = {"ACTIVE", "DISABLED"}) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", description = "Version lue, pour le verrouillage optimiste")
        Long version) {}
