package com.expertiz.sentinel.core.shared.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Action à auditer, publiée par un module avec {@code ApplicationEventPublisher#publishEvent} dans sa transaction
 * (spec §7.3). {@code details} ne contient que des champs déclarés auditables ; jamais de mot de passe, de hash, de
 * jeton, de cookie ni de clé (spec §9.5). Sans acteur explicite, l'acteur est déduit du contexte de sécurité.
 */
public record AuditEvent(String action, String targetType, UUID targetId, Map<String, Object> details, AuditActor actor) {

    public AuditEvent {
        Objects.requireNonNull(action, "action");
        // Copie qui admet les valeurs null : un diff peut aller de null à une valeur.
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static AuditEvent of(String action, String targetType, UUID targetId, Map<String, ?> details) {
        return new AuditEvent(action, targetType, targetId, details == null ? null : new LinkedHashMap<>(details), null);
    }

    public AuditEvent withActor(AuditActor actor) {
        return new AuditEvent(action, targetType, targetId, details, actor);
    }
}
