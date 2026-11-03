package com.expertiz.sentinel.core.identity.internal.session;

import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.identity.internal.session.LoginThrottleService.StartedBlock;
import com.expertiz.sentinel.core.identity.internal.session.SessionAudit.FailureReason;
import com.expertiz.sentinel.core.shared.audit.AuditActor;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login (spec §8.4). Une seule transaction, validée quelle que soit l'issue : compteurs de limitation et audit restent
 * enregistrés derrière un 401. Les échecs sont indiscernables pour le client ; l'identifiant saisi n'est jamais stocké
 * ni journalisé, seulement son empreinte.
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    // Identifiant inconnu : même coût Argon2 qu'un mauvais mot de passe, pour ne pas révéler l'existence d'un compte.
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Fingerprints fingerprints;
    private final LoginThrottleService throttle;
    private final RefreshTokenService refreshTokens;
    private final SessionTokens sessionTokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Counter blockedAttempts;

    LoginService(UserRepository users, PasswordEncoder encoder, Fingerprints fingerprints, LoginThrottleService throttle,
            RefreshTokenService refreshTokens, SessionTokens sessionTokens, ApplicationEventPublisher events, Clock clock,
            MeterRegistry meters) {
        this.users = users;
        this.encoder = encoder;
        this.fingerprints = fingerprints;
        this.throttle = throttle;
        this.refreshTokens = refreshTokens;
        this.sessionTokens = sessionTokens;
        this.events = events;
        this.clock = clock;
        this.blockedAttempts = Counter.builder("sentinel.login.blocked")
                .description("Tentatives de login refusées pendant un blocage")
                .register(meters);
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginOutcome login(String identifier, String password, String ip, String userAgent) {
        String identifierFp = fingerprints.identifier(identifier);
        String ipFp = fingerprints.ip(ip == null ? "unknown" : ip);

        // Avant toute vérification : pas de coût Argon2, pas de prolongation, pas d'audit individuel.
        Optional<Duration> blocked = throttle.blockedFor(identifierFp, ipFp);
        if (blocked.isPresent()) {
            blockedAttempts.increment();
            log.info("Login refusé pendant un blocage ({} s restantes)", blocked.get().toSeconds());
            return new LoginOutcome.Throttled(blocked.get());
        }

        Optional<User> found = users.findByEmail(Fingerprints.normalizeIdentifier(identifier));
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            return failed(FailureReason.UNKNOWN_IDENTIFIER, null, identifierFp, ipFp);
        }
        User user = found.get();
        if (!encoder.matches(password, user.getPasswordHash())) {
            return failed(FailureReason.BAD_PASSWORD, user.getId(), identifierFp, ipFp);
        }
        Instant now = clock.instant();
        // Mot de passe correct : ces refus ne sont pas une attaque, ils n'incrémentent pas les compteurs.
        if (user.getStatus() != UserStatus.ACTIVE) {
            events.publishEvent(SessionAudit.loginFailed(FailureReason.ACCOUNT_DISABLED, user.getId(), identifierFp));
            return new LoginOutcome.InvalidCredentials();
        }
        if (user.getPasswordExpiresAt() != null && !user.getPasswordExpiresAt().isAfter(now)) {
            events.publishEvent(SessionAudit.loginFailed(FailureReason.TEMP_PASSWORD_EXPIRED, user.getId(), identifierFp));
            return new LoginOutcome.InvalidCredentials();
        }

        throttle.resetAccountIp(identifierFp, ipFp);
        users.touchLastLogin(user.getId(), now);
        NewRefreshToken refresh = refreshTokens.startFamily(user.getId(), ip, userAgent);
        events.publishEvent(AuditEvent.of(SessionAudit.LOGIN_SUCCEEDED, SessionAudit.TARGET_USER, user.getId(), Map.of())
                .withActor(AuditActor.user(user.getId(), user.getEmail())));
        return new LoginOutcome.Success(sessionTokens.open(user, refresh));
    }

    private LoginOutcome failed(FailureReason reason, UUID userId, String identifierFp, String ipFp) {
        events.publishEvent(SessionAudit.loginFailed(reason, userId, identifierFp));
        for (StartedBlock block : throttle.recordFailure(identifierFp, ipFp)) {
            events.publishEvent(SessionAudit.throttled(block, userId, identifierFp, AuditActor.anonymous()));
        }
        return new LoginOutcome.InvalidCredentials();
    }
}
