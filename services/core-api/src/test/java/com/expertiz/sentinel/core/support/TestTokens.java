package com.expertiz.sentinel.core.support;

import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Jetons construits à la main pour les tests de falsification. {@link #validClaims} reproduit un jeton légitime : chaque
 * test n'en modifie qu'un élément, et un témoin vérifie que le jeton non modifié est accepté.
 */
public final class TestTokens {

    public static final JOSEObjectType ACCESS_TOKEN_TYPE = new JOSEObjectType("at+jwt");

    private TestTokens() {}

    public static JWTClaimsSet.Builder validClaims(TestUser user, Instant now, List<String> permissions) {
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        return new JWTClaimsSet.Builder()
                .issuer("vps-sentinel")
                .audience("core-api")
                .subject(user.id().toString())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(Duration.ofMinutes(15))))
                .jwtID(UUID.randomUUID().toString())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .claim("perms", permissions)
                .claim("pcr", false);
    }

    public static JWSHeader.Builder validHeader(JWSAlgorithm algorithm) {
        return new JWSHeader.Builder(algorithm).type(ACCESS_TOKEN_TYPE);
    }

    public static String sign(JWSHeader header, JWTClaimsSet claims, JWSSigner signer) {
        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Signé RS256 avec la clé de l'application, en-tête {@code typ: at+jwt}. */
    public static String signRs256(RSAPrivateKey key, JWTClaimsSet claims) {
        return sign(validHeader(JWSAlgorithm.RS256).build(), claims, new RSASSASigner(key));
    }
}
