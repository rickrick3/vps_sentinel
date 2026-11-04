package com.expertiz.sentinel.core.identity;

import static com.expertiz.sentinel.core.support.TestTokens.sign;
import static com.expertiz.sentinel.core.support.TestTokens.signRs256;
import static com.expertiz.sentinel.core.support.TestTokens.validHeader;
import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.secrets.SentinelSecrets;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import com.expertiz.sentinel.core.support.TestSecrets;
import com.expertiz.sentinel.core.support.TestTokens;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Jetons falsifiés, expirés ou incomplets (spec §8.1, §13.3) : tous donnent le même 401 en Problem Details, sans
 * révéler la raison du refus. Chaque cas ne modifie qu'un élément d'un jeton qu'un témoin montre accepté.
 */
class TokenTamperingIT extends IntegrationTest {

    static final List<String> VIEWER_PERMISSIONS = List.of("project:read", "server:read");

    @Autowired
    SentinelSecrets secrets;

    TestUser user;

    @BeforeEach
    void user() {
        user = fixtures.createUser(RoleCode.VIEWER);
    }

    private JWTClaimsSet.Builder validClaims() {
        return TestTokens.validClaims(user, clock.instant(), VIEWER_PERMISSIONS);
    }

    private RSAPrivateKey appKey() {
        return secrets.jwtPrivateKey();
    }

    private void assertAccepted(String token) {
        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk();
    }

    private void assertRejected(String authorization) {
        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:sentinel:problem:unauthorized")
                .jsonPath("$.detail").isEqualTo("Jeton d'accès invalide ou expiré.");
    }

    private void assertTokenRejected(String token) {
        assertRejected(bearer(token));
    }

    // Témoins : sans eux, un refus pourrait venir d'une erreur du test et non du contrôle visé.

    @Test
    void issuedTokenIsAccepted() {
        assertAccepted(fixtures.accessToken(user));
    }

    @Test
    void handBuiltTokenIsAcceptedWhenUnmodified() {
        assertAccepted(signRs256(appKey(), validClaims().build()));
    }

    // Signature et algorithme

    @Test
    void unsignedAlgNone() {
        PlainHeader header = new PlainHeader.Builder().type(TestTokens.ACCESS_TOKEN_TYPE).build();
        assertTokenRejected(new PlainJWT(header, validClaims().build()).serialize());
    }

    @Test
    void signedWithAnotherRsaKey() {
        KeyPair other = TestSecrets.rsa(3072);
        assertTokenRejected(signRs256((RSAPrivateKey) other.getPrivate(), validClaims().build()));
    }

    @Test
    void hs256SignedWithThePublicKeyBytes() throws Exception {
        // Confusion d'algorithme : la clé publique, connue de tous, utilisée comme secret HMAC.
        byte[] publicKey = secrets.jwtPublicKey().getEncoded();
        assertTokenRejected(sign(validHeader(JWSAlgorithm.HS256).build(), validClaims().build(), new MACSigner(publicKey)));
    }

    @Test
    void rs512WithTheApplicationKey() {
        // Seul RS256 est accepté, même avec la bonne clé.
        assertTokenRejected(sign(validHeader(JWSAlgorithm.RS512).build(), validClaims().build(), new RSASSASigner(appKey())));
    }

    @Test
    void embeddedJwkHeaderIsIgnored() {
        KeyPair attacker = TestSecrets.rsa(3072);
        RSAKey attackerJwk = new RSAKey.Builder((RSAPublicKey) attacker.getPublic()).build();
        assertTokenRejected(sign(validHeader(JWSAlgorithm.RS256).jwk(attackerJwk).build(), validClaims().build(),
                new RSASSASigner((RSAPrivateKey) attacker.getPrivate())));
    }

    @Test
    void payloadModifiedAfterSigning() {
        String[] parts = fixtures.accessToken(user).split("\\.");
        String escalated = validClaims().claim("perms", List.of("user:manage")).claim("role", "SUPER_ADMIN").build().toString();
        String forged = parts[0] + "." + Base64URL.encode(escalated.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertTokenRejected(forged);
    }

    @Test
    void signatureRemoved() {
        String[] parts = fixtures.accessToken(user).split("\\.");
        assertTokenRejected(parts[0] + "." + parts[1] + ".");
    }

    // Type de jeton

    @ParameterizedTest
    @ValueSource(strings = {"JWT", "application/jwt", "id+jwt", ""})
    void wrongTypeHeader(String type) {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256);
        if (!type.isEmpty()) {
            header.type(new JOSEObjectType(type));
        }
        assertTokenRejected(sign(header.build(), validClaims().build(), new RSASSASigner(appKey())));
    }

    // Dates

    @Test
    void acceptedUntilExpiryThenRejected() {
        clock.set(Instant.parse("2026-11-03T10:00:00Z"));
        String token = fixtures.accessToken(user);

        clock.advance(Duration.ofMinutes(15));
        assertAccepted(token);

        clock.advance(Duration.ofSeconds(1));
        assertTokenRejected(token);
    }

    @Test
    void notYetValid() {
        Instant now = clock.instant();
        assertTokenRejected(signRs256(appKey(), validClaims().notBeforeTime(Date.from(now.plusSeconds(60))).build()));
    }

    @Test
    void issuedInTheFuture() {
        Instant future = clock.instant().plusSeconds(60);
        assertTokenRejected(signRs256(appKey(), validClaims()
                .issueTime(Date.from(future))
                .expirationTime(Date.from(future.plus(Duration.ofMinutes(15))))
                .build()));
    }

    // Claims

    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "sub", "iat", "exp", "jti", "email", "role", "perms", "pcr"})
    void missingRequiredClaim(String claim) {
        assertTokenRejected(signRs256(appKey(), validClaims().claim(claim, null).build()));
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void invalidClaimValue(String claim, Object value) {
        assertTokenRejected(signRs256(appKey(), validClaims().claim(claim, value).build()));
    }

    static Stream<Arguments> invalidClaims() {
        return Stream.of(
                Arguments.of("iss", "https://evil.example"),
                Arguments.of("iss", "VPS-SENTINEL"),
                Arguments.of("aud", "monitoring"),
                Arguments.of("aud", List.of("monitoring", "other")),
                Arguments.of("sub", "not-a-uuid"),
                Arguments.of("sub", ""),
                Arguments.of("jti", ""),
                Arguments.of("email", ""),
                Arguments.of("email", 42),
                Arguments.of("role", "ROOT"),
                Arguments.of("role", "viewer"),
                Arguments.of("perms", "server:read project:read"),
                Arguments.of("perms", List.of(1, 2)),
                Arguments.of("pcr", "false"),
                Arguments.of("pcr", 0));
    }

    // En-tête Authorization

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "Bearer ", "Bearer not-a-jwt", "Bearer a.b", "Bearer a.b.c", "Bearer x y"})
    void malformedBearerHeader(String authorization) {
        assertRejected(authorization);
    }

    @Test
    void tokenInQueryParameterIsIgnored() {
        http.get()
                .uri("/test/secured?access_token={t}", fixtures.accessToken(user))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Authentification requise.");
    }

    @Test
    void basicAuthenticationIsNotSupported() {
        String credentials = Base64.getEncoder().encodeToString((user.email() + ":" + user.password()).getBytes(StandardCharsets.UTF_8));

        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Authentification requise.");
    }

    @Test
    void rejectionDoesNotRevealTheReason() {
        clock.set(Instant.parse("2026-11-03T10:00:00Z"));
        String token = fixtures.accessToken(user);
        clock.advance(Duration.ofHours(1));

        String body = new String(http.get()
                        .uri("/test/secured")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .exchange()
                        .expectStatus().isUnauthorized()
                        .expectBody()
                        .returnResult()
                        .getResponseBody(),
                StandardCharsets.UTF_8);

        assertThat(body).doesNotContainIgnoringCase("expired").doesNotContain("Jwt").doesNotContain("2026-11-03")
                .doesNotContain(token);
    }
}
