package com.expertiz.sentinel.core.identity.internal.session;

import com.expertiz.sentinel.core.identity.internal.session.LoginThrottleService.StartedBlock;
import com.expertiz.sentinel.core.shared.audit.AuditActor;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Événements d'audit des sessions (spec §9.5). Ils ne portent jamais l'identifiant saisi, un mot de passe ou un jeton :
 * seulement l'empreinte HMAC de l'identifiant.
 */
final class SessionAudit {

    static final String TARGET_USER = "USER";

    static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    static final String LOGIN_FAILED = "LOGIN_FAILED";
    static final String LOGIN_THROTTLED = "LOGIN_THROTTLED";
    static final String LOGOUT = "LOGOUT";
    static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    static final String PASSWORD_CHANGE_FAILED = "PASSWORD_CHANGE_FAILED";

    /** Motif d'un échec de login ({@code details.reason}). */
    enum FailureReason {
        UNKNOWN_IDENTIFIER,
        BAD_PASSWORD,
        ACCOUNT_DISABLED,
        TEMP_PASSWORD_EXPIRED
    }

    private SessionAudit() {}

    /** Échec de login, acteur anonyme ; la cible est le compte s'il existe (spec §9.5). */
    static AuditEvent loginFailed(FailureReason reason, UUID userId, String identifierFingerprint) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason.name());
        details.put("identifierFingerprint", identifierFingerprint);
        return AuditEvent.of(LOGIN_FAILED, userId == null ? null : TARGET_USER, userId, details)
                .withActor(AuditActor.anonymous());
    }

    /** Début d'un blocage. Les tentatives pendant le blocage ne sont pas auditées une à une (spec §8.4). */
    static AuditEvent throttled(StartedBlock block, UUID userId, String identifierFingerprint, AuditActor actor) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", block.scope().name());
        details.put("failureCount", block.failureCount());
        details.put("blockedSeconds", block.duration().toSeconds());
        details.put("identifierFingerprint", identifierFingerprint);
        return AuditEvent.of(LOGIN_THROTTLED, userId == null ? null : TARGET_USER, userId, details).withActor(actor);
    }
}
