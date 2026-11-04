package com.expertiz.sentinel.core.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ligne du journal d'audit (spec §9.5). {@code details} est l'objet JSON enregistré, {@code ip} l'adresse sans masque,
 * sous forme canonique.
 */
public record AuditLogResponse(UUID id, Instant occurredAt, String actorType, UUID actorUserId, String actorEmail,
        String action, String targetType, UUID targetId, Map<String, Object> details, String ip, String correlationId) {}
