package com.expertiz.sentinel.core.identity.internal.session;

import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.identity.internal.security.SentinelSecurityProperties;
import com.expertiz.sentinel.core.identity.internal.session.RefreshTokenRepository.Rotated;
import com.expertiz.sentinel.core.identity.internal.session.RefreshTokenRepository.Similarity;
import com.expertiz.sentinel.core.identity.internal.session.RefreshTokenRepository.Stored;
import com.expertiz.sentinel.core.shared.audit.AuditActor;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.persistence.UuidV7;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens (spec §8.2, §8.3) : valeur opaque de 256 bits tirée par {@link SecureRandom}, encodée en base64url ; la
 * base ne garde que son hash SHA-256. Un jeton ne produit jamais plus d'un successeur, et un jeton déjà tourné présenté
 * de nouveau révoque toute sa famille, quel que soit le délai.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final SessionTokens sessionTokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration idleTtl;
    private final Duration sessionMaxAge;
    private final SecureRandom random = new SecureRandom();

    RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, SessionTokens sessionTokens,
            ApplicationEventPublisher events, Clock clock, SentinelSecurityProperties properties) {
        this.tokens = tokens;
        this.users = users;
        this.sessionTokens = sessionTokens;
        this.events = events;
        this.clock = clock;
        this.idleTtl = properties.refreshIdleTtl();
        this.sessionMaxAge = properties.sessionMaxAge();
    }

    /** Nouvelle famille au login : la durée absolue de la session court à partir de maintenant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public NewRefreshToken startFamily(UUID userId, String ip, String userAgent) {
        Instant now = clock.instant();
        return create(userId, UuidV7.next(), UuidV7.next(), now, now, ip, userAgent);
    }

    /**
     * {@code POST /auth/refresh} (spec §8.2). Transaction validée quelle que soit l'issue : les révocations d'un refus
     * (compte désactivé, réutilisation) sont conservées.
     */
    @Transactional
    public RefreshOutcome rotate(String rawToken, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            return new RefreshOutcome.Rejected();
        }
        Instant now = clock.instant();
        String hash = hash(rawToken);
        UUID successorId = UuidV7.next();
        Optional<Rotated> rotated = tokens.rotate(hash, successorId, now, now.minus(sessionMaxAge));
        if (rotated.isPresent()) {
            Rotated presented = rotated.get();
            Optional<User> user = users.findById(presented.userId()).filter(u -> u.getStatus() == UserStatus.ACTIVE);
            if (user.isEmpty()) {
                tokens.revokeFamilyOfInactiveUser(presented.familyId(), presented.id(), now);
                return new RefreshOutcome.Rejected();
            }
            NewRefreshToken successor = create(presented.userId(), successorId, presented.familyId(),
                    presented.sessionStartedAt(), now, ip, userAgent);
            return new RefreshOutcome.Rotated(sessionTokens.open(user.get(), successor));
        }
        // Rien n'a tourné. La réutilisation est testée avant l'expiration : un jeton tourné puis expiré compte aussi.
        tokens.findByHash(hash)
                .filter(stored -> RevokedReason.ROTATED.name().equals(stored.revokedReason()))
                .ifPresent(stored -> reuseDetected(stored, now, ip, userAgent));
        return new RefreshOutcome.Rejected();
    }

    /** {@code POST /auth/logout} (spec §8.5) : la famille du jeton est révoquée, même si le jeton a expiré. */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokens.findByHash(hash(rawToken)).ifPresent(stored -> {
            if (tokens.revokeFamily(stored.familyId(), RevokedReason.LOGOUT, clock.instant()) > 0) {
                events.publishEvent(AuditEvent.of(SessionAudit.LOGOUT, SessionAudit.TARGET_USER, stored.userId(),
                                Map.of("familyId", stored.familyId().toString()))
                        .withActor(actorOf(stored.userId())));
            }
        });
    }

    /** Révoque toutes les sessions d'un utilisateur ; renvoie le nombre de jetons révoqués. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAllForUser(UUID userId, RevokedReason reason) {
        return tokens.revokeAllForUser(userId, reason, clock.instant());
    }

    /**
     * Réutilisation d'un jeton tourné (spec §8.3) : toute la famille est révoquée. L'audit n'est écrit que si un jeton
     * actif a été coupé ; sur une famille déjà inactive, un rejeu ne produit qu'un avertissement dans les logs.
     */
    private void reuseDetected(Stored presented, Instant now, String ip, String userAgent) {
        int revoked = tokens.revokeFamily(presented.familyId(), RevokedReason.REUSE_DETECTED, now);
        if (revoked == 0) {
            log.warn("Refresh token déjà tourné présenté sur une famille inactive (famille {})", presented.familyId());
            return;
        }
        Similarity similarity = Optional.ofNullable(presented.replacedBy())
                .flatMap(successor -> tokens.compare(successor, ip, userAgent))
                .orElse(new Similarity(false, false));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("familyId", presented.familyId().toString());
        details.put("secondsSinceRotation", Duration.between(presented.revokedAt(), now).toSeconds());
        details.put("sameIp", similarity.sameIp());
        details.put("sameUserAgent", similarity.sameUserAgent());
        events.publishEvent(AuditEvent.of(SessionAudit.TOKEN_REUSE_DETECTED, SessionAudit.TARGET_USER, presented.userId(), details)
                .withActor(actorOf(presented.userId())));
        log.warn("Réutilisation d'un refresh token tourné : famille {} révoquée ({} jeton(s) actif(s))", presented.familyId(), revoked);
    }

    // L'utilisateur du jeton, avec l'email de son compte s'il existe encore (spec §7.3).
    private AuditActor actorOf(UUID userId) {
        return AuditActor.user(userId, users.findById(userId).map(User::getEmail).orElse(null));
    }

    private NewRefreshToken create(UUID userId, UUID id, UUID familyId, Instant sessionStartedAt, Instant now, String ip,
            String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        NewRefreshToken token = new NewRefreshToken(raw, id, familyId, now.plus(idleTtl), sessionStartedAt);
        tokens.insert(token, userId, hash(raw), now, ip, userAgent);
        return token;
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
