package com.expertiz.sentinel.core.identity.internal.session;

import com.expertiz.sentinel.core.shared.persistence.UuidV7;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Compteurs de limitation (spec §8.4, §9.2), indexés par empreintes HMAC uniquement. */
@Repository
class LoginThrottleRepository {

    /** Un compteur repart de 1 quand le dernier échec date de plus d'une heure. */
    private static final Duration COUNTER_RESET = Duration.ofHours(1);

    private final JdbcClient jdbc;

    LoginThrottleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Counter(UUID id, int failureCount) {}

    /** Ajoute un échec en une instruction atomique : des échecs concurrents sont tous comptés. */
    Counter recordFailure(ThrottleScope scope, String accountFp, String ipFp, Instant now) {
        return jdbc.sql("""
                        INSERT INTO login_throttles (id, scope, account_fp, ip_fp, failure_count, first_failure_at, last_failure_at)
                        VALUES (:id, :scope, :accountFp, :ipFp, 1, :now, :now)
                        ON CONFLICT (scope, coalesce(account_fp, ''), ip_fp) DO UPDATE SET
                          failure_count    = CASE WHEN login_throttles.last_failure_at < :resetBefore THEN 1
                                                  ELSE login_throttles.failure_count + 1 END,
                          first_failure_at = CASE WHEN login_throttles.last_failure_at < :resetBefore THEN :now
                                                  ELSE login_throttles.first_failure_at END,
                          last_failure_at  = :now
                        RETURNING id, failure_count""")
                .param("id", UuidV7.next())
                .param("scope", scope.name())
                .param("accountFp", accountFp)
                .param("ipFp", ipFp)
                .param("now", at(now))
                .param("resetBefore", at(now.minus(COUNTER_RESET)))
                .query((rs, row) -> new Counter(rs.getObject("id", UUID.class), rs.getInt("failure_count")))
                .single();
    }

    void block(UUID id, Instant until) {
        jdbc.sql("UPDATE login_throttles SET blocked_until = :until WHERE id = :id")
                .param("until", at(until))
                .param("id", id)
                .update();
    }

    /** Fin du blocage en cours le plus long : portée compte + IP, et portée IP si demandé. */
    Optional<Instant> blockedUntil(String accountFp, String ipFp, boolean includeIpScope, Instant now) {
        return jdbc.sql("""
                        SELECT blocked_until FROM login_throttles
                         WHERE ip_fp = :ipFp
                           AND blocked_until > :now
                           AND ((scope = 'ACCOUNT_IP' AND account_fp = :accountFp) OR (:includeIp AND scope = 'IP'))
                         ORDER BY blocked_until DESC
                         LIMIT 1""")
                .param("ipFp", ipFp)
                .param("now", at(now))
                .param("accountFp", accountFp)
                .param("includeIp", includeIpScope)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    void deleteAccountIp(String accountFp, String ipFp) {
        jdbc.sql("DELETE FROM login_throttles WHERE scope = 'ACCOUNT_IP' AND account_fp = :accountFp AND ip_fp = :ipFp")
                .param("accountFp", accountFp)
                .param("ipFp", ipFp)
                .update();
    }

    /** Tous les compteurs compte + IP d'un compte, quelle que soit l'IP. */
    int deleteAccount(String accountFp) {
        return jdbc.sql("DELETE FROM login_throttles WHERE scope = 'ACCOUNT_IP' AND account_fp = :accountFp")
                .param("accountFp", accountFp)
                .update();
    }

    /** Compteurs sans échec depuis {@code lastFailureBefore} et sans blocage en cours. */
    int deleteStale(Instant lastFailureBefore, Instant now) {
        return jdbc.sql("""
                        DELETE FROM login_throttles
                         WHERE last_failure_at < :before AND (blocked_until IS NULL OR blocked_until < :now)""")
                .param("before", at(lastFailureBefore))
                .param("now", at(now))
                .update();
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
