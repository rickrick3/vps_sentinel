package com.expertiz.sentinel.core.identity.internal.security;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.secrets.SentinelSecrets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signature et validation des access tokens avec la paire RSA des secrets (spec §6.6, §8.1). */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(SentinelSecrets secrets) {
        return NimbusJwtEncoder.withKeyPair(secrets.jwtPublicKey(), secrets.jwtPrivateKey()).build();
    }

    /** RS256 seul, avec la clé publique de l'application. Tout claim émis par {@link AccessTokenIssuer} est exigé. */
    @Bean
    JwtDecoder jwtDecoder(SentinelSecrets secrets, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(secrets.jwtPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validator(clock));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(Clock clock) {
        // L'émetteur et le vérificateur sont la même application, sur la même horloge : aucune tolérance de décalage.
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        return new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTypeValidator(JwtClaims.TYPE),
                timestamps,
                issuedInThePast(clock),
                new JwtIssuerValidator(JwtClaims.ISSUER),
                new JwtAudienceValidator(JwtClaims.AUDIENCE),
                required(JwtClaimNames.SUB, value -> value instanceof String s && isUuid(s)),
                required(JwtClaimNames.JTI, value -> value instanceof String s && !s.isBlank()),
                required(JwtClaims.EMAIL, value -> value instanceof String s && !s.isBlank()),
                required(JwtClaims.ROLE, value -> value instanceof String s && isRole(s)),
                required(JwtClaims.PERMS, value -> value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)),
                required(JwtClaims.PCR, value -> value instanceof Boolean)));
    }

    /** {@code iat} présent et pas dans le futur. ({@code JwtIssuedAtValidator} refuse aussi un {@code iat} de plus d'une minute.) */
    private static OAuth2TokenValidator<Jwt> issuedInThePast(Clock clock) {
        OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The iat claim is not valid", null);
        return jwt -> jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(clock.instant())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }

    // Typé Object : un claim d'un autre type JSON doit être refusé, pas lever une ClassCastException.
    private static OAuth2TokenValidator<Jwt> required(String claim, Predicate<Object> valid) {
        return new JwtClaimValidator<>(claim, valid);
    }

    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isRole(String value) {
        return Arrays.stream(RoleCode.values()).anyMatch(role -> role.name().equals(value));
    }
}
