package com.expertiz.sentinel.core.shared.audit;

import java.util.UUID;

/**
 * Auteur d'une action auditée. Seul un utilisateur authentifié porte un id et un email : l'email enregistré est la
 * copie figée de son compte, jamais une valeur saisie au login (spec §9.5).
 */
public record AuditActor(ActorType type, UUID userId, String email) {

    public AuditActor {
        if (type == ActorType.USER && userId == null) {
            throw new IllegalArgumentException("Un acteur USER doit porter l'id de l'utilisateur");
        }
        if (type != ActorType.USER && (userId != null || email != null)) {
            throw new IllegalArgumentException("Seul un acteur USER porte un id et un email");
        }
    }

    public static AuditActor user(UUID userId, String email) {
        return new AuditActor(ActorType.USER, userId, email);
    }

    public static AuditActor anonymous() {
        return new AuditActor(ActorType.ANONYMOUS, null, null);
    }

    public static AuditActor system() {
        return new AuditActor(ActorType.SYSTEM, null, null);
    }
}
