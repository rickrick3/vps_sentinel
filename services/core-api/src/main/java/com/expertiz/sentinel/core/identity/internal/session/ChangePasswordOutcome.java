package com.expertiz.sentinel.core.identity.internal.session;

import java.time.Duration;

/**
 * Issue d'un changement de mot de passe. Un mauvais mot de passe actuel est un résultat : le compteur de limitation et
 * l'audit doivent être validés avant la réponse 400 (spec §7.3, §8.5).
 */
public sealed interface ChangePasswordOutcome {

    record Changed(AuthTokens tokens) implements ChangePasswordOutcome {}

    record WrongCurrentPassword() implements ChangePasswordOutcome {}

    record Throttled(Duration retryAfter) implements ChangePasswordOutcome {}
}
