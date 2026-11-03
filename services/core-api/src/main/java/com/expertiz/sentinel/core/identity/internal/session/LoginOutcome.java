package com.expertiz.sentinel.core.identity.internal.session;

import java.time.Duration;

/**
 * Issue d'un login. Un échec est un résultat, pas une exception : la transaction qui enregistre les compteurs et
 * l'audit doit être validée avant que le contrôleur ne réponde 401 ou 429 (spec §7.3).
 */
public sealed interface LoginOutcome {

    record Success(AuthTokens tokens) implements LoginOutcome {}

    record InvalidCredentials() implements LoginOutcome {}

    record Throttled(Duration retryAfter) implements LoginOutcome {}
}
