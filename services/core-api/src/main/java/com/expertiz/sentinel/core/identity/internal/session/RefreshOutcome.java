package com.expertiz.sentinel.core.identity.internal.session;

/** Issue d'un refresh. Un refus est un résultat : les révocations qu'il entraîne doivent être validées (spec §8.2). */
public sealed interface RefreshOutcome {

    record Rotated(AuthTokens tokens) implements RefreshOutcome {}

    record Rejected() implements RefreshOutcome {}
}
