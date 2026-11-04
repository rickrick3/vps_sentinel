package com.expertiz.sentinel.core.identity.web;

import java.time.Instant;

/** Réponse à la réinitialisation : le mot de passe temporaire n'est renvoyé qu'ici, une seule fois (spec §8.6). */
public record TemporaryPasswordResponse(String temporaryPassword, Instant temporaryPasswordExpiresAt) {

    @Override
    public String toString() {
        return "TemporaryPasswordResponse[expiresAt=" + temporaryPasswordExpiresAt + "]";
    }
}
