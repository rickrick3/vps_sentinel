package com.expertiz.sentinel.core.identity.internal.session;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token qui vient d'être créé. {@code rawValue} ne sort que dans le cookie de la réponse : la base n'en garde
 * que le hash.
 */
public record NewRefreshToken(String rawValue, UUID id, UUID familyId, Instant expiresAt, Instant sessionStartedAt) {

    @Override
    public String toString() {
        return "NewRefreshToken[id=" + id + ", familyId=" + familyId + "]";
    }
}
