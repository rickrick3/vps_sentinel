package com.expertiz.sentinel.core.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/login}. L'identifiant est du texte libre, normalisé au moment de la recherche. La borne de
 * 1024 caractères limite le coût d'Argon2 ; la politique de 128 caractères ne vaut que pour définir un mot de passe.
 */
public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 1024) String password) {

    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
