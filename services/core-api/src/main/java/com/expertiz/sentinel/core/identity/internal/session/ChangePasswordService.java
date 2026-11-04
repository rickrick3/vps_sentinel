package com.expertiz.sentinel.core.identity.internal.session;

import com.expertiz.sentinel.core.identity.internal.model.PasswordPolicy;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.identity.internal.session.LoginThrottleService.StartedBlock;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changement de mot de passe (spec §8.5), dans cet ordre : limitation de la portée compte + IP, vérification du mot de
 * passe actuel, politique du nouveau. Le succès révoque toutes les sessions de l'utilisateur et en ouvre une nouvelle.
 * L'acteur de l'audit est l'utilisateur du jeton.
 */
@Service
public class ChangePasswordService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Fingerprints fingerprints;
    private final LoginThrottleService throttle;
    private final RefreshTokenService refreshTokens;
    private final SessionTokens sessionTokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ChangePasswordService(UserRepository users, PasswordEncoder encoder, Fingerprints fingerprints,
            LoginThrottleService throttle, RefreshTokenService refreshTokens, SessionTokens sessionTokens,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.fingerprints = fingerprints;
        this.throttle = throttle;
        this.refreshTokens = refreshTokens;
        this.sessionTokens = sessionTokens;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ChangePasswordOutcome change(UUID userId, String current, String next, String ip, String userAgent) {
        User user = users.findById(userId)
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProblemType.UNAUTHORIZED, "Authentification requise."));
        // Même clé que le login : un blocage obtenu par l'un vaut pour l'autre.
        String accountFp = fingerprints.identifier(user.getEmail());
        String ipFp = fingerprints.ip(ip == null ? "unknown" : ip);

        Optional<Duration> blocked = throttle.accountIpBlockedFor(accountFp, ipFp);
        if (blocked.isPresent()) {
            return new ChangePasswordOutcome.Throttled(blocked.get());
        }
        if (!encoder.matches(current, user.getPasswordHash())) {
            events.publishEvent(AuditEvent.of(SessionAudit.PASSWORD_CHANGE_FAILED, SessionAudit.TARGET_USER, userId, Map.of()));
            for (StartedBlock block : throttle.recordAccountIpFailure(accountFp, ipFp)) {
                events.publishEvent(SessionAudit.throttled(block, userId, accountFp, null));
            }
            return new ChangePasswordOutcome.WrongCurrentPassword();
        }
        // Les erreurs de politique annulent la transaction : le mot de passe actuel était correct, rien n'est à garder.
        PasswordPolicy.check("newPassword", next, user.getEmail());
        if (next.equals(current)) {
            throw new ApiException(ProblemType.VALIDATION_ERROR, "Un ou plusieurs champs sont invalides.",
                    List.of(new FieldViolation("newPassword", "Le nouveau mot de passe doit être différent de l'actuel.")));
        }

        user.changePassword(encoder.encode(next), clock.instant());
        int revoked = refreshTokens.revokeAllForUser(userId, RevokedReason.PASSWORD_CHANGED);
        throttle.resetAccountIp(accountFp, ipFp);
        NewRefreshToken refresh = refreshTokens.startFamily(userId, ip, userAgent);
        events.publishEvent(AuditEvent.of(SessionAudit.PASSWORD_CHANGED, SessionAudit.TARGET_USER, userId,
                Map.of("sessionsRevoked", revoked)));
        return new ChangePasswordOutcome.Changed(sessionTokens.open(user, refresh));
    }
}
