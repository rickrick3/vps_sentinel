package com.expertiz.sentinel.core.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/change-password}. Les deux valeurs sont bornées à 1024 caractères avant tout hachage ; la
 * politique (12 à 128 caractères) s'applique ensuite au nouveau mot de passe.
 */
public record ChangePasswordRequest(@NotBlank @Size(max = 1024) String currentPassword,
        @NotBlank @Size(max = 1024) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
