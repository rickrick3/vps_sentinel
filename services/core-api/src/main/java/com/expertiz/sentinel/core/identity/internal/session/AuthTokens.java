package com.expertiz.sentinel.core.identity.internal.session;

import com.expertiz.sentinel.core.identity.internal.security.IssuedAccessToken;
import com.expertiz.sentinel.core.identity.web.MeResponse;

/** Paire de jetons d'une session ouverte ou renouvelée, avec le profil renvoyé au client (spec §8.4). */
public record AuthTokens(IssuedAccessToken access, NewRefreshToken refresh, MeResponse user) {}
