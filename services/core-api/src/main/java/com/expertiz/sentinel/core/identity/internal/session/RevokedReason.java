package com.expertiz.sentinel.core.identity.internal.session;

/** Motif de révocation d'un refresh token ({@code refresh_tokens.revoked_reason}, spec §9.2). */
public enum RevokedReason {
    ROTATED,
    LOGOUT,
    REUSE_DETECTED,
    USER_DISABLED,
    ROLE_CHANGED,
    PASSWORD_CHANGED,
    PASSWORD_RESET
}
