package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.security.AccessTokenIssuer;
import com.expertiz.sentinel.core.identity.internal.security.IssuedAccessToken;
import com.expertiz.sentinel.core.shared.secrets.SentinelSecrets;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Access token RS256 (spec §8.1) : claims calculés depuis la base, dates issues de l'horloge applicative. */
class AccessTokenIssuerIT extends IntegrationTest {

    @Autowired
    AccessTokenIssuer issuer;

    @Autowired
    UserRepository users;

    @Autowired
    SentinelSecrets secrets;

    private IssuedAccessToken issue(TestUser user) {
        return issuer.issue(users.findById(user.id()).orElseThrow());
    }

    @Test
    void claimsComeFromTheDatabase() throws Exception {
        clock.set(Instant.parse("2026-11-03T10:00:00.750Z"));
        TestUser user = fixtures.createUser(RoleCode.DEVELOPER);

        IssuedAccessToken token = issue(user);

        SignedJWT jwt = SignedJWT.parse(token.value());
        assertThat(jwt.verify(new RSASSAVerifier(secrets.jwtPublicKey()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getType().getType()).isEqualTo("at+jwt");

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("vps-sentinel");
        assertThat(claims.getAudience()).containsExactly("core-api");
        assertThat(claims.getSubject()).isEqualTo(user.id().toString());
        assertThat(claims.getStringClaim("email")).isEqualTo(user.email());
        assertThat(claims.getStringClaim("role")).isEqualTo("DEVELOPER");
        assertThat(claims.getStringListClaim("perms")).containsExactly("project:read", "project:write", "server:read");
        assertThat(claims.getBooleanClaim("pcr")).isFalse();
        assertThat(UUID.fromString(claims.getJWTID())).isNotNull();
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(Instant.parse("2026-11-03T10:15:00Z"));
        assertThat(token.expiresInSeconds()).isEqualTo(900);
        assertThat(claims.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "aud", "sub", "iat", "exp", "jti", "email", "role", "perms", "pcr");
    }

    @Test
    void pcrFollowsTheAccount() throws Exception {
        TestUser user = fixtures.createUser(RoleCode.VIEWER);

        String token = fixtures.accessTokenWithPcr(user);

        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getBooleanClaim("pcr")).isTrue();
    }

    @Test
    void roleChangeIsReflectedInTheNextToken() throws Exception {
        TestUser user = fixtures.createUser(RoleCode.VIEWER);
        appJdbc().sql("UPDATE user_roles SET role_id = (SELECT id FROM roles WHERE code = 'INFRA_ADMIN') WHERE user_id = :id")
                .param("id", user.id())
                .update();

        JWTClaimsSet claims = SignedJWT.parse(issue(user).value()).getJWTClaimsSet();

        assertThat(claims.getStringClaim("role")).isEqualTo("INFRA_ADMIN");
        assertThat(claims.getStringListClaim("perms")).isEqualTo(List.copyOf(PermissionMatrixIT.EXPECTED.get(RoleCode.INFRA_ADMIN)
                .stream().sorted().toList()));
    }

    @Test
    void eachTokenHasItsOwnId() throws Exception {
        TestUser user = fixtures.createUser(RoleCode.VIEWER);

        String first = SignedJWT.parse(issue(user).value()).getJWTClaimsSet().getJWTID();
        String second = SignedJWT.parse(issue(user).value()).getJWTClaimsSet().getJWTID();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void refusesADisabledAccount() {
        TestUser user = fixtures.createUser(RoleCode.SUPER_ADMIN);
        appJdbc().sql("UPDATE users SET status = 'DISABLED' WHERE id = :id").param("id", user.id()).update();

        assertThatThrownBy(() -> issue(user)).isInstanceOf(IllegalStateException.class);
    }
}
