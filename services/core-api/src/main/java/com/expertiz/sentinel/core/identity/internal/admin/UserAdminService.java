package com.expertiz.sentinel.core.identity.internal.admin;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.RoleRepository;
import com.expertiz.sentinel.core.identity.internal.model.TemporaryPasswordGenerator;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.identity.internal.security.SentinelSecurityProperties;
import com.expertiz.sentinel.core.identity.internal.session.LoginThrottleService;
import com.expertiz.sentinel.core.identity.internal.session.RefreshTokenService;
import com.expertiz.sentinel.core.identity.internal.session.RevokedReason;
import com.expertiz.sentinel.core.shared.audit.AuditDiff;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.text.TextRules;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration des utilisateurs (spec §8.8, §10.1). Garde-fous :
 *
 * <ul>
 *   <li>personne ne modifie son propre rôle, ne se désactive ni ne se supprime : 409 {@code self-modification-forbidden} ;
 *   <li>le dernier SUPER_ADMIN actif n'est ni rétrogradé, ni désactivé, ni supprimé : 409 {@code last-super-admin}.
 *       Toute opération qui peut réduire leur nombre prend d'abord le verrou, puis lit la cible et compte (spec §7.2) ;
 *   <li>rétrograder, désactiver ou supprimer révoque toutes les sessions de la cible. Son access token reste valable au
 *       plus 15 minutes (spec §8.8).
 * </ul>
 *
 * Chaque changement est audité avec ses seuls champs modifiés ; une suppression, avec un instantané (spec §9.5).
 */
@Service
public class UserAdminService {

    private static final String TARGET_USER = "USER";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final TemporaryPasswordGenerator temporaryPasswords;
    private final SuperAdminLock lock;
    private final RefreshTokenService refreshTokens;
    private final LoginThrottleService throttle;
    private final Fingerprints fingerprints;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration temporaryPasswordTtl;

    UserAdminService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
            TemporaryPasswordGenerator temporaryPasswords, SuperAdminLock lock, RefreshTokenService refreshTokens,
            LoginThrottleService throttle, Fingerprints fingerprints, ApplicationEventPublisher events, Clock clock,
            SentinelSecurityProperties properties) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.temporaryPasswords = temporaryPasswords;
        this.lock = lock;
        this.refreshTokens = refreshTokens;
        this.throttle = throttle;
        this.fingerprints = fingerprints;
        this.events = events;
        this.clock = clock;
        this.temporaryPasswordTtl = properties.temporaryPasswordTtl();
    }

    /** Mot de passe temporaire : renvoyé une seule fois, jamais journalisé ni stocké en clair (spec §8.6). */
    public record IssuedTemporaryPassword(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedTemporaryPassword[expiresAt=" + expiresAt + "]";
        }
    }

    public record CreatedUser(User user, IssuedTemporaryPassword temporaryPassword) {}

    /** Email déjà normalisé par le constructeur de {@link User} ; un doublon devient un 409 sur {@code email}. */
    @Transactional
    public CreatedUser create(String email, String fullName, RoleCode role) {
        Instant now = clock.instant();
        IssuedTemporaryPassword temporary = issueTemporaryPassword(now);
        User user = users.saveAndFlush(new User(email, TextRules.clean(fullName), encoder.encode(temporary.value()),
                roles.findByCode(role).orElseThrow(), true, temporary.expiresAt(), now));
        events.publishEvent(AuditEvent.of("USER_CREATED", TARGET_USER, user.getId(), user.auditState()));
        return new CreatedUser(user, temporary);
    }

    @Transactional
    public User patch(UUID actorId, UUID id, PatchUserCommand command) {
        if (command.changesRoleOrStatus()) {
            lock.acquire();
        }
        User user = find(id);
        if (!Objects.equals(user.getVersion(), command.version())) {
            throw new ApiException(ProblemType.VERSION_CONFLICT, "Cet élément a été modifié par quelqu'un d'autre.");
        }
        RoleCode role = command.role().orElse(user.getRole().getCode());
        UserStatus status = command.status().orElse(user.getStatus());
        boolean roleChanges = role != user.getRole().getCode();
        boolean statusChanges = status != user.getStatus();
        if ((roleChanges || statusChanges) && id.equals(actorId)) {
            throw selfModification();
        }
        if (isActiveSuperAdmin(user) && (role != RoleCode.SUPER_ADMIN || status != UserStatus.ACTIVE)) {
            ensureAnotherActiveSuperAdmin(id);
        }

        Map<String, Object> before = user.auditState();
        Instant now = clock.instant();
        command.fullName().filter(name -> !name.equals(user.getFullName())).ifPresent(name -> user.rename(name, now));
        if (roleChanges) {
            user.changeRole(roles.findByCode(role).orElseThrow(), now);
        }
        if (statusChanges) {
            if (status == UserStatus.DISABLED) {
                user.disable(now);
            } else {
                user.enable(now);
            }
        }
        users.flush();

        Map<String, Map<String, Object>> changes = AuditDiff.changes(before, user.auditState());
        if (status == UserStatus.DISABLED && statusChanges) {
            refreshTokens.revokeAllForUser(id, RevokedReason.USER_DISABLED);
        } else if (roleChanges) {
            refreshTokens.revokeAllForUser(id, RevokedReason.ROLE_CHANGED);
        }
        audit("USER_UPDATED", id, changes, "fullName");
        audit("USER_ROLE_CHANGED", id, changes, "role");
        audit(status == UserStatus.DISABLED ? "USER_DISABLED" : "USER_ENABLED", id, changes, "status");
        return user;
    }

    @Transactional
    public void delete(UUID actorId, UUID id) {
        lock.acquire();
        User user = find(id);
        if (id.equals(actorId)) {
            throw selfModification();
        }
        if (isActiveSuperAdmin(user)) {
            ensureAnotherActiveSuperAdmin(id);
        }
        Map<String, Object> snapshot = user.auditState();
        user.delete(clock.instant());
        users.flush();
        refreshTokens.revokeAllForUser(id, RevokedReason.USER_DISABLED);
        events.publishEvent(AuditEvent.of("USER_DELETED", TARGET_USER, id, snapshot));
    }

    /**
     * Nouveau mot de passe temporaire (spec §10.1) : sessions révoquées, changement imposé, compteurs de limitation du
     * compte supprimés pour que l'utilisateur puisse se reconnecter tout de suite.
     */
    @Transactional
    public IssuedTemporaryPassword resetPassword(UUID id) {
        User user = find(id);
        Instant now = clock.instant();
        IssuedTemporaryPassword temporary = issueTemporaryPassword(now);
        user.resetPassword(encoder.encode(temporary.value()), temporary.expiresAt(), now);
        users.flush();
        int sessions = refreshTokens.revokeAllForUser(id, RevokedReason.PASSWORD_RESET);
        int throttles = throttle.clearAccount(fingerprints.identifier(user.getEmail()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sessionsRevoked", sessions);
        details.put("throttlesCleared", throttles);
        events.publishEvent(AuditEvent.of("USER_PASSWORD_RESET", TARGET_USER, id, details));
        return temporary;
    }

    private IssuedTemporaryPassword issueTemporaryPassword(Instant now) {
        return new IssuedTemporaryPassword(temporaryPasswords.generate(), now.plus(temporaryPasswordTtl));
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ProblemType.NOT_FOUND, "Utilisateur introuvable."));
    }

    private static boolean isActiveSuperAdmin(User user) {
        return user.getRole().getCode() == RoleCode.SUPER_ADMIN && user.getStatus() == UserStatus.ACTIVE;
    }

    /** Sous le verrou des SUPER_ADMIN : le décompte ne peut pas changer avant la fin de la transaction. */
    private void ensureAnotherActiveSuperAdmin(UUID target) {
        if (users.countByRoleCodeAndStatusAndIdNot(RoleCode.SUPER_ADMIN, UserStatus.ACTIVE, target) == 0) {
            throw new ApiException(ProblemType.LAST_SUPER_ADMIN,
                    "Le dernier super-administrateur actif ne peut être ni rétrogradé, ni désactivé, ni supprimé.");
        }
    }

    private static ApiException selfModification() {
        return new ApiException(ProblemType.SELF_MODIFICATION_FORBIDDEN,
                "Vous ne pouvez ni changer votre propre rôle, ni vous désactiver, ni vous supprimer.");
    }

    private void audit(String action, UUID id, Map<String, Map<String, Object>> changes, String field) {
        if (changes.containsKey(field)) {
            events.publishEvent(AuditEvent.of(action, TARGET_USER, id, Map.of("changes", Map.of(field, changes.get(field)))));
        }
    }
}
