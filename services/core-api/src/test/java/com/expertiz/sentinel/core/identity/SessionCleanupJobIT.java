package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.session.SessionCleanupJob;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * Purge quotidienne (spec §9.2) : sessions commencées il y a plus de 14 jours, et compteurs sans échec depuis 24 h ni
 * blocage en cours. Rien de ce qui peut encore servir n'est supprimé.
 */
class SessionCleanupJobIT extends IntegrationTest {

    @Autowired
    SessionCleanupJob job;

    @Autowired
    ScheduledTaskHolder scheduler;

    private UUID session(UUID userId, Duration startedAgo) {
        Instant started = clock.instant().minus(startedAgo);
        return ownerJdbc()
                .sql("""
                        INSERT INTO refresh_tokens (user_id, family_id, token_hash, session_started_at, issued_at, expires_at)
                        VALUES (:user, :family, :hash, :started, :started, :expires) RETURNING id""")
                .param("user", userId)
                .param("family", UUID.randomUUID())
                .param("hash", Fixtures.uniqueFingerprint() + Fixtures.uniqueFingerprint())
                .param("started", Timestamp.from(started))
                .param("expires", Timestamp.from(started.plus(Duration.ofHours(24))))
                .query(UUID.class)
                .single();
    }

    private UUID throttle(Duration lastFailureAgo, Duration blockedFor) {
        Instant last = clock.instant().minus(lastFailureAgo);
        return ownerJdbc()
                .sql("""
                        INSERT INTO login_throttles (scope, ip_fp, failure_count, first_failure_at, last_failure_at, blocked_until)
                        VALUES ('IP', :ip, 20, :last, :last, :blocked) RETURNING id""")
                .param("ip", Fixtures.uniqueFingerprint())
                .param("last", Timestamp.from(last))
                .param("blocked", blockedFor == null ? null : Timestamp.from(clock.instant().plus(blockedFor)))
                .query(UUID.class)
                .single();
    }

    private boolean exists(String table, UUID id) {
        return ownerJdbc().sql("SELECT count(*) FROM " + table + " WHERE id = :id").param("id", id).query(Long.class).single() == 1;
    }

    @Test
    void purgesOldSessionsAndStaleThrottles() {
        UUID userId = fixtures.createUser(RoleCode.VIEWER).id();
        UUID oldSession = session(userId, Duration.ofDays(15));
        UUID recentSession = session(userId, Duration.ofDays(13));
        UUID staleThrottle = throttle(Duration.ofHours(25), null);
        UUID recentThrottle = throttle(Duration.ofHours(23), null);
        UUID blockedThrottle = throttle(Duration.ofHours(25), Duration.ofMinutes(5));

        job.purge();

        assertThat(exists("refresh_tokens", oldSession)).isFalse();
        assertThat(exists("refresh_tokens", recentSession)).isTrue();
        assertThat(exists("login_throttles", staleThrottle)).isFalse();
        assertThat(exists("login_throttles", recentThrottle)).isTrue();
        assertThat(exists("login_throttles", blockedThrottle)).isTrue();
    }

    @Test
    void registeredWithTheSchedulerDaily() {
        // Le runnable enveloppe la méthode planifiée ; son nom qualifié l'identifie.
        assertThat(scheduler.getScheduledTasks())
                .filteredOn(task -> task.getTask().getRunnable().toString().endsWith(SessionCleanupJob.class.getName() + ".purge"))
                .singleElement()
                .satisfies(task -> assertThat(task.getTask())
                        .isInstanceOfSatisfying(CronTask.class, cron -> assertThat(cron.getExpression()).isEqualTo("0 30 3 * * *")));
    }
}
