package com.expertiz.sentinel.core.identity.internal.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purge quotidienne (spec §9.2), instance unique en phase 1. Au-delà de la durée absolue de 7 jours, aucun jeton d'une
 * famille ne peut plus servir : 14 jours laissent une marge à l'analyse d'un incident.
 */
@Component
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);
    private static final Duration SESSION_RETENTION = Duration.ofDays(14);
    private static final Duration THROTTLE_RETENTION = Duration.ofHours(24);

    private final RefreshTokenRepository tokens;
    private final LoginThrottleRepository throttles;
    private final Clock clock;

    SessionCleanupJob(RefreshTokenRepository tokens, LoginThrottleRepository throttles, Clock clock) {
        this.tokens = tokens;
        this.throttles = throttles;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        Instant now = clock.instant();
        int sessions = tokens.deleteSessionsStartedBefore(now.minus(SESSION_RETENTION));
        int counters = throttles.deleteStale(now.minus(THROTTLE_RETENTION), now);
        log.info("Purge des sessions : {} refresh token(s), {} compteur(s) de limitation supprimés", sessions, counters);
    }
}
