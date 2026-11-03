package com.expertiz.sentinel.core.identity.internal.security;

import com.expertiz.sentinel.core.identity.internal.model.PermissionMatrix;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Émet les access tokens RS256 (spec §8.1). Les claims sont toujours calculés depuis l'état du compte en base, jamais
 * recopiés d'un ancien jeton ; les dates viennent de l'horloge applicative.
 */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final PermissionMatrix matrix;
    private final Clock clock;
    private final Duration ttl;

    AccessTokenIssuer(JwtEncoder encoder, PermissionMatrix matrix, Clock clock, SentinelSecurityProperties properties) {
        this.encoder = encoder;
        this.matrix = matrix;
        this.clock = clock;
        this.ttl = properties.accessTokenTtl();
    }

    /** Jeton pour un compte actif. Émettre pour un compte désactivé est une erreur de programmation. */
    public IssuedAccessToken issue(User user) {
        if (user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            throw new IllegalStateException("Access token demandé pour un compte inactif : " + user.getId());
        }
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        List<String> permissions = List.copyOf(matrix.permissionsOf(user.getRole().getCode()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtClaims.ISSUER)
                .audience(List.of(JwtClaims.AUDIENCE))
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(JwtClaims.EMAIL, user.getEmail())
                .claim(JwtClaims.ROLE, user.getRole().getCode().name())
                .claim(JwtClaims.PERMS, permissions)
                .claim(JwtClaims.PCR, user.isMustChangePassword())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type(JwtClaims.TYPE).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, ttl.toSeconds());
    }
}
