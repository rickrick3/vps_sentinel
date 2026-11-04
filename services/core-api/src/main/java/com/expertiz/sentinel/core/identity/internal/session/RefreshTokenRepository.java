package com.expertiz.sentinel.core.identity.internal.session;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Refresh tokens (spec §8.2, §9.2) : seul le hash SHA-256 du jeton est stocké. */
@Repository
class RefreshTokenRepository {

    private final JdbcClient jdbc;

    RefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Jeton présenté et tourné : son successeur, d'id {@code replaced_by}, reste à insérer. */
    record Rotated(UUID id, UUID userId, UUID familyId, Instant sessionStartedAt) {}

    record Stored(UUID id, UUID userId, UUID familyId, Instant revokedAt, String revokedReason, UUID replacedBy) {}

    void insert(NewRefreshToken token, UUID userId, String tokenHash, Instant issuedAt, String ip, String userAgent) {
        jdbc.sql("""
                        INSERT INTO refresh_tokens (id, user_id, family_id, token_hash, session_started_at, issued_at, expires_at,
                                                    user_agent, ip)
                        VALUES (:id, :userId, :familyId, :hash, :sessionStartedAt, :issuedAt, :expiresAt, :userAgent,
                                CAST(:ip AS inet))""")
                .param("id", token.id())
                .param("userId", userId)
                .param("familyId", token.familyId())
                .param("hash", tokenHash)
                .param("sessionStartedAt", at(token.sessionStartedAt()))
                .param("issuedAt", at(issuedAt))
                .param("expiresAt", at(token.expiresAt()))
                .param("userAgent", userAgent)
                .param("ip", ip)
                .update();
    }

    /**
     * Rotation atomique (spec §8.2) : une seule instruction marque le jeton tourné s'il est encore valide. Deux requêtes
     * concurrentes avec le même jeton ne peuvent pas toutes deux réussir : la seconde attend le verrou de ligne, puis ne
     * modifie plus rien.
     */
    Optional<Rotated> rotate(String tokenHash, UUID successorId, Instant now, Instant sessionNotBefore) {
        return jdbc.sql("""
                        UPDATE refresh_tokens
                           SET revoked_at = :now, revoked_reason = 'ROTATED', replaced_by = :successor
                         WHERE token_hash = :hash
                           AND revoked_at IS NULL
                           AND expires_at > :now
                           AND session_started_at > :sessionNotBefore
                        RETURNING id, user_id, family_id, session_started_at""")
                .param("now", at(now))
                .param("successor", successorId)
                .param("hash", tokenHash)
                .param("sessionNotBefore", at(sessionNotBefore))
                .query((rs, row) -> new Rotated(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getObject("family_id", UUID.class), instant(rs, "session_started_at")))
                .optional();
    }

    Optional<Stored> findByHash(String tokenHash) {
        return jdbc.sql("""
                        SELECT id, user_id, family_id, revoked_at, revoked_reason, replaced_by
                          FROM refresh_tokens WHERE token_hash = :hash""")
                .param("hash", tokenHash)
                .query((rs, row) -> new Stored(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getObject("family_id", UUID.class), instant(rs, "revoked_at"), rs.getString("revoked_reason"),
                        rs.getObject("replaced_by", UUID.class)))
                .optional();
    }

    /** La requête vient-elle de la même IP et du même navigateur que la rotation ? Comparé en base : jamais en clair. */
    record Similarity(boolean sameIp, boolean sameUserAgent) {}

    Optional<Similarity> compare(UUID tokenId, String ip, String userAgent) {
        return jdbc.sql("""
                        SELECT coalesce(ip = CAST(:ip AS inet), false) AS same_ip,
                               coalesce(user_agent = :userAgent, false) AS same_ua
                          FROM refresh_tokens WHERE id = :id""")
                .param("ip", ip)
                .param("userAgent", userAgent)
                .param("id", tokenId)
                .query((rs, row) -> new Similarity(rs.getBoolean("same_ip"), rs.getBoolean("same_ua")))
                .optional();
    }

    /** Révoque les jetons encore actifs de la famille ; renvoie leur nombre. */
    int revokeFamily(UUID familyId, RevokedReason reason, Instant now) {
        return jdbc.sql("""
                        UPDATE refresh_tokens SET revoked_at = :now, revoked_reason = :reason
                         WHERE family_id = :family AND revoked_at IS NULL""")
                .param("now", at(now))
                .param("reason", reason.name())
                .param("family", familyId)
                .update();
    }

    /** Compte inactif : la famille est révoquée, jeton présenté compris, sans successeur (spec §8.2, étape 3). */
    void revokeFamilyOfInactiveUser(UUID familyId, UUID presentedId, Instant now) {
        jdbc.sql("""
                        UPDATE refresh_tokens SET revoked_at = :now, revoked_reason = 'USER_DISABLED', replaced_by = NULL
                         WHERE family_id = :family AND (revoked_at IS NULL OR id = :presented)""")
                .param("now", at(now))
                .param("family", familyId)
                .param("presented", presentedId)
                .update();
    }

    int revokeAllForUser(UUID userId, RevokedReason reason, Instant now) {
        return jdbc.sql("""
                        UPDATE refresh_tokens SET revoked_at = :now, revoked_reason = :reason
                         WHERE user_id = :user AND revoked_at IS NULL""")
                .param("now", at(now))
                .param("reason", reason.name())
                .param("user", userId)
                .update();
    }

    int deleteSessionsStartedBefore(Instant before) {
        return jdbc.sql("DELETE FROM refresh_tokens WHERE session_started_at < :before").param("before", at(before)).update();
    }

    static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
