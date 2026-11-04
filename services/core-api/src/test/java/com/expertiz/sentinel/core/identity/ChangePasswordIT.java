package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.support.AuthClient;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import com.nimbusds.jwt.SignedJWT;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * {@code POST /api/v1/auth/change-password} (spec §8.5) : authentifié par Bearer, limité par la portée compte + IP,
 * révoque toutes les sessions et ouvre une nouvelle paire de jetons. Un mauvais mot de passe actuel est une erreur de
 * champ (400), pas un 401 qui déclencherait un refresh côté client.
 */
class ChangePasswordIT extends IntegrationTest {

    static final String NEW_PASSWORD = "Another-Password-2!";
    static final String WRONG_CURRENT = "Wrong-Current-Password-1!";

    @Autowired
    Fingerprints fingerprints;

    TestUser user;
    String ip;
    Response session;

    @BeforeEach
    void session() {
        user = fixtures.createUser(RoleCode.DEVOPS);
        ip = Fixtures.uniqueIp();
        session = auth.login(user.email(), user.password(), ip);
    }

    private Map<String, Object> account() {
        return ownerJdbc()
                .sql("SELECT password_hash, must_change_password, password_expires_at FROM users WHERE id = :id")
                .param("id", user.id())
                .query()
                .singleRow();
    }

    private Integer accountIpFailures() {
        return ownerJdbc()
                .sql("""
                        SELECT failure_count FROM login_throttles
                         WHERE scope = 'ACCOUNT_IP' AND account_fp = :account AND ip_fp = :ip""")
                .param("account", fingerprints.identifier(user.email()))
                .param("ip", fingerprints.ip(ip))
                .query(Integer.class)
                .optional()
                .orElse(null);
    }

    private static void assertFieldError(Response response, String field, String message) {
        assertThat(response.status()).isEqualTo(400);
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:validation-error");
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactly(field);
        assertThat((List<String>) response.json("$.errors[*].message")).containsExactly(message);
    }

    @Test
    void successRevokesAllSessionsAndIssuesNewPair() {
        Response other = auth.login(user.email(), user.password(), Fixtures.uniqueIp());
        String correlationId = AuthClient.newRequestId();

        Response changed = auth.changePassword(session.accessToken(), user.password(), NEW_PASSWORD, ip, correlationId);

        assertThat(changed.status()).isEqualTo(200);
        assertThat((Boolean) changed.json("$.user.mustChangePassword")).isFalse();
        assertThat(changed.setCookie()).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(auth.refresh(session.refreshCookie(), ip).status()).isEqualTo(401);
        assertThat(auth.refresh(other.refreshCookie(), ip).status()).isEqualTo(401);
        assertThat(auth.refresh(changed.refreshCookie(), ip).status()).isEqualTo(200);
        assertThat(ownerJdbc().sql("""
                                SELECT count(*) FROM refresh_tokens
                                 WHERE user_id = :id AND revoked_reason = 'PASSWORD_CHANGED'""")
                        .param("id", user.id())
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(account()).containsEntry("must_change_password", false).containsEntry("password_expires_at", null);
        Map<String, Object> audit = ownerJdbc()
                .sql("SELECT actor_type, actor_user_id, target_id FROM audit_logs WHERE action = 'PASSWORD_CHANGED' AND correlation_id = :c")
                .param("c", correlationId)
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_user_id", user.id()).containsEntry("target_id", user.id());
        assertThat(auth.login(user.email(), user.password(), Fixtures.uniqueIp()).status()).isEqualTo(401);
        assertThat(auth.login(user.email(), NEW_PASSWORD, Fixtures.uniqueIp()).status()).isEqualTo(200);
    }

    @Test
    void pcrUserCanChange() throws Exception {
        appJdbc().sql("UPDATE users SET must_change_password = true, password_expires_at = :at WHERE id = :id")
                .param("at", Timestamp.from(clock.instant().plus(Duration.ofHours(72))))
                .param("id", user.id())
                .update();
        Response forced = auth.login(user.email(), user.password(), Fixtures.uniqueIp());
        assertThat(SignedJWT.parse(forced.accessToken()).getJWTClaimsSet().getBooleanClaim("pcr")).isTrue();

        Response changed = auth.changePassword(forced.accessToken(), user.password(), NEW_PASSWORD, ip);

        assertThat(changed.status()).isEqualTo(200);
        assertThat(SignedJWT.parse(changed.accessToken()).getJWTClaimsSet().getBooleanClaim("pcr")).isFalse();
        assertThat(account()).containsEntry("must_change_password", false).containsEntry("password_expires_at", null);
        http.get()
                .uri("/test/secured")
                .header(HttpHeaders.AUTHORIZATION, bearer(changed.accessToken()))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void wrongCurrentIs400OnField() {
        Object hashBefore = account().get("password_hash");
        String correlationId = AuthClient.newRequestId();

        Response response = auth.changePassword(session.accessToken(), WRONG_CURRENT, NEW_PASSWORD, ip, correlationId);

        assertFieldError(response, "currentPassword", "Mot de passe actuel incorrect.");
        assertThat(response.body()).doesNotContain(WRONG_CURRENT).doesNotContain(NEW_PASSWORD);
        assertThat(account().get("password_hash")).isEqualTo(hashBefore);
        assertThat(accountIpFailures()).isEqualTo(1);
        assertThat(ownerJdbc().sql("SELECT count(*) FROM audit_logs WHERE action = 'PASSWORD_CHANGE_FAILED' AND correlation_id = :c")
                        .param("c", correlationId)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(auth.refresh(session.refreshCookie(), ip).status()).as("session conservée").isEqualTo(200);
    }

    @Test
    void throttledIs429BeforeAnyVerification() {
        for (int i = 0; i < 5; i++) {
            assertThat(auth.changePassword(session.accessToken(), WRONG_CURRENT, NEW_PASSWORD, ip).status()).isEqualTo(400);
        }
        Object hashBefore = account().get("password_hash");

        Response response = auth.changePassword(session.accessToken(), user.password(), NEW_PASSWORD, ip);

        assertThat(response.status()).isEqualTo(429);
        assertThat(response.headers().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat((String) response.json("$.type")).isEqualTo("urn:sentinel:problem:login-throttled");
        assertThat(account().get("password_hash")).isEqualTo(hashBefore);
    }

    @Test
    void sharesTheAccountIpScopeWithLogin() {
        for (int i = 0; i < 5; i++) {
            auth.login(user.email(), WRONG_CURRENT, ip);
        }

        assertThat(auth.changePassword(session.accessToken(), user.password(), NEW_PASSWORD, ip).status()).isEqualTo(429);
        assertThat(auth.changePassword(session.accessToken(), user.password(), NEW_PASSWORD, Fixtures.uniqueIp()).status())
                .as("autre IP")
                .isEqualTo(200);
    }

    @Test
    void newPasswordPolicyIs400OnNewPassword() {
        Object hashBefore = account().get("password_hash");

        assertFieldError(auth.changePassword(session.accessToken(), user.password(), "too-short", ip),
                "newPassword", "12 caractères minimum.");
        assertFieldError(auth.changePassword(session.accessToken(), user.password(), user.email().toUpperCase(), ip),
                "newPassword", "Le mot de passe doit être différent de l'email.");

        assertThat(account().get("password_hash")).isEqualTo(hashBefore);
        assertThat(accountIpFailures()).isNull();
    }

    @Test
    void sameAsCurrentRejected() {
        assertFieldError(auth.changePassword(session.accessToken(), user.password(), user.password(), ip),
                "newPassword", "Le nouveau mot de passe doit être différent de l'actuel.");
    }

    @Test
    void blankFieldsAre400() {
        Response response = auth.changePassword(session.accessToken(), "", " ", ip);

        assertThat(response.status()).isEqualTo(400);
        assertThat((List<String>) response.json("$.errors[*].field")).containsExactlyInAnyOrder("currentPassword", "newPassword");
    }

    @Test
    void requiresAnAccessToken() {
        http.post()
                .uri("/api/v1/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", user.password(), "newPassword", NEW_PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized();
        http.post()
                .uri("/api/v1/auth/change-password")
                .cookie(AuthClient.COOKIE, session.refreshCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", user.password(), "newPassword", NEW_PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void disabledAccountCannotChange() {
        appJdbc().sql("UPDATE users SET status = 'DISABLED' WHERE id = :id").param("id", user.id()).update();

        assertThat(auth.changePassword(session.accessToken(), user.password(), NEW_PASSWORD, ip).status()).isEqualTo(401);
    }

    @Test
    void textPlainIs415() {
        http.post()
                .uri("/api/v1/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                .contentType(MediaType.TEXT_PLAIN)
                .body("currentPassword=x")
                .exchange()
                .expectStatus().isEqualTo(415);
    }
}
