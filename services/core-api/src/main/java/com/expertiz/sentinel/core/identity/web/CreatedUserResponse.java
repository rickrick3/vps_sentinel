package com.expertiz.sentinel.core.identity.web;

import java.time.Instant;

/** Réponse à la création : le mot de passe temporaire n'est renvoyé qu'ici, une seule fois (spec §8.6). */
public record CreatedUserResponse(UserResponse user, String temporaryPassword, Instant temporaryPasswordExpiresAt) {

    @Override
    public String toString() {
        return "CreatedUserResponse[user=" + user.id() + "]";
    }
}
