package com.expertiz.sentinel.core.identity.web;

import com.expertiz.sentinel.core.identity.internal.session.AuthTokens;

/** Réponse du login, du refresh et du changement de mot de passe (spec §8.4). Le refresh token part dans le cookie. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {

    static AuthResponse of(AuthTokens tokens) {
        return new AuthResponse(tokens.access().value(), "Bearer", tokens.access().expiresInSeconds(), tokens.user());
    }

    @Override
    public String toString() {
        return "AuthResponse[user=" + user.id() + "]";
    }
}
