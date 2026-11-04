package com.expertiz.sentinel.core.infrastructure.internal;

import java.util.List;

/** Valeurs distinctes existantes parmi les VPS non supprimés, triées : auto-complétion du formulaire (spec §10.1). */
public record ServerFacetsResponse(List<String> providers, List<String> regions, List<String> tags) {}
