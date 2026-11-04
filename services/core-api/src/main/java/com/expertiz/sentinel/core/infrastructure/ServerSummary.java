package com.expertiz.sentinel.core.infrastructure;

import java.util.UUID;

/** Ce que les autres modules savent d'un VPS. */
public record ServerSummary(UUID id, String name, AdminStatus adminStatus) {}
