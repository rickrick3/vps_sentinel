package com.expertiz.sentinel.core.identity.internal.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limitation progressive des tentatives (spec §8.4). Les clés sont des empreintes HMAC : jamais l'identifiant ni l'IP
 * en clair. Aucun blocage n'est permanent et aucun ne vise un compte seul. Les écritures se font dans la transaction de
 * l'appelant, validée avec l'audit même quand la réponse est une erreur (spec §7.3).
 */
@Service
public class LoginThrottleService {

    private static final Duration FIRST_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofMinutes(15);
    // 30 s × 2^5 dépasse déjà le plafond de 15 min.
    private static final int MAX_DOUBLINGS = 5;

    private final LoginThrottleRepository throttles;
    private final Clock clock;

    LoginThrottleService(LoginThrottleRepository throttles, Clock clock) {
        this.throttles = throttles;
        this.clock = clock;
    }

    /** Blocage commencé par un échec : portée, nombre d'échecs et durée. */
    public record StartedBlock(ThrottleScope scope, int failureCount, Duration duration) {}

    /** {@code min(30 s × 2^(n − seuil), 15 min)} à partir du seuil, aucun délai avant. */
    public static Duration delayFor(int failures, int threshold) {
        if (failures < threshold) {
            return Duration.ZERO;
        }
        int doublings = failures - threshold;
        return doublings >= MAX_DOUBLINGS ? MAX_DELAY : min(FIRST_DELAY.multipliedBy(1L << doublings), MAX_DELAY);
    }

    /** Temps restant si la portée compte + IP ou la portée IP est bloquée (login). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Duration> blockedFor(String accountFp, String ipFp) {
        return remaining(throttles.blockedUntil(accountFp, ipFp, true, clock.instant()));
    }

    /** Temps restant si la portée compte + IP est bloquée (changement de mot de passe). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Duration> accountIpBlockedFor(String accountFp, String ipFp) {
        return remaining(throttles.blockedUntil(accountFp, ipFp, false, clock.instant()));
    }

    /** Échec de login : les deux portées sont incrémentées. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StartedBlock> recordFailure(String accountFp, String ipFp) {
        List<StartedBlock> started = new ArrayList<>();
        count(ThrottleScope.ACCOUNT_IP, accountFp, ipFp, started);
        count(ThrottleScope.IP, null, ipFp, started);
        return started;
    }

    /** Mauvais mot de passe actuel au changement de mot de passe : portée compte + IP seule (spec §8.4). */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StartedBlock> recordAccountIpFailure(String accountFp, String ipFp) {
        List<StartedBlock> started = new ArrayList<>();
        count(ThrottleScope.ACCOUNT_IP, accountFp, ipFp, started);
        return started;
    }

    /** Succès : la portée compte + IP repart de zéro, pas la portée IP (spec §8.4). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resetAccountIp(String accountFp, String ipFp) {
        throttles.deleteAccountIp(accountFp, ipFp);
    }

    private void count(ThrottleScope scope, String accountFp, String ipFp, List<StartedBlock> started) {
        Instant now = clock.instant();
        LoginThrottleRepository.Counter counter = throttles.recordFailure(scope, accountFp, ipFp, now);
        Duration delay = delayFor(counter.failureCount(), scope.threshold());
        if (!delay.isZero()) {
            throttles.block(counter.id(), now.plus(delay));
            started.add(new StartedBlock(scope, counter.failureCount(), delay));
        }
    }

    private Optional<Duration> remaining(Optional<Instant> blockedUntil) {
        Instant now = clock.instant();
        return blockedUntil.map(until -> Duration.between(now, until));
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
