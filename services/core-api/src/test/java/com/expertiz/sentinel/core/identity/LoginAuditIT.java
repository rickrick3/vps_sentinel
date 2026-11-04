package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.support.AuthClient;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.Fixtures.TestUser;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Audit du login (spec §9.5, §13.5) : l'identifiant saisi n'est jamais stocké ni journalisé, seulement son empreinte ;
 * l'état de sécurité et l'audit sont validés malgré la réponse d'erreur.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoginAuditIT extends IntegrationTest {

    @Autowired
    Fingerprints fingerprints;

    private Map<String, Object> auditRow(String correlationId, String action) {
        return ownerJdbc()
                .sql("""
                        SELECT actor_type, actor_user_id, actor_email, target_type, target_id, host(ip) AS ip,
                               details ->> 'reason' AS reason, details ->> 'identifierFingerprint' AS fingerprint,
                               details ->> 'scope' AS scope, (details ->> 'failureCount')::int AS failure_count,
                               (details ->> 'blockedSeconds')::int AS blocked_seconds
                          FROM audit_logs WHERE correlation_id = :c AND action = :action""")
                .param("c", correlationId)
                .param("action", action)
                .query()
                .singleRow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"S3cr3t-Passw0rd!", "p@ssw0rd-1234567"})
    void rawIdentifierNeverStoredNorLogged(String typed, CapturedOutput output) {
        String correlationId = AuthClient.newRequestId();

        assertThat(auth.login(typed, "x", Fixtures.uniqueIp(), correlationId).status()).isEqualTo(401);

        String all = ownerJdbc().sql("SELECT string_agg(a::text, '|') FROM audit_logs a WHERE correlation_id = :c")
                .param("c", correlationId)
                .query(String.class)
                .single();
        assertThat(all).isNotNull().doesNotContain(typed).contains(fingerprints.identifier(typed));
        assertThat(output.getAll()).doesNotContain(typed);
    }

    @Test
    void fingerprintIsStableForTheSameIdentifier() {
        String typed = Fixtures.uniqueEmail();
        String first = AuthClient.newRequestId();
        String second = AuthClient.newRequestId();

        auth.login(typed, "x", Fixtures.uniqueIp(), first);
        auth.login("  " + typed.toUpperCase() + " ", "x", Fixtures.uniqueIp(), second);

        assertThat(auditRow(first, "LOGIN_FAILED").get("fingerprint"))
                .isEqualTo(auditRow(second, "LOGIN_FAILED").get("fingerprint"))
                .isEqualTo(fingerprints.identifier(typed));
    }

    @Test
    void knownAccountFailureTargetsTheUser() {
        TestUser user = fixtures.createUser(RoleCode.VIEWER);
        String ip = Fixtures.uniqueIp();
        String correlationId = AuthClient.newRequestId();

        auth.login(user.email(), "Wrong-Password-1!", ip, correlationId);

        assertThat(auditRow(correlationId, "LOGIN_FAILED"))
                .containsEntry("actor_type", "ANONYMOUS")
                .containsEntry("actor_user_id", null)
                .containsEntry("actor_email", null)
                .containsEntry("target_type", "USER")
                .containsEntry("target_id", user.id())
                .containsEntry("reason", "BAD_PASSWORD")
                .containsEntry("ip", ip);
    }

    @Test
    void unknownIdentifierHasNoTarget() {
        String correlationId = AuthClient.newRequestId();

        auth.login(Fixtures.uniqueEmail(), "Wrong-Password-1!", Fixtures.uniqueIp(), correlationId);

        assertThat(auditRow(correlationId, "LOGIN_FAILED"))
                .containsEntry("actor_type", "ANONYMOUS")
                .containsEntry("target_type", null)
                .containsEntry("target_id", null)
                .containsEntry("reason", "UNKNOWN_IDENTIFIER");
    }

    @Test
    void throttleStartAuditedOnce() {
        TestUser user = fixtures.createUser(RoleCode.VIEWER);
        String ip = Fixtures.uniqueIp();
        String fifth = AuthClient.newRequestId();
        for (int i = 0; i < 4; i++) {
            auth.login(user.email(), "Wrong-Password-1!", ip);
        }
        auth.login(user.email(), "Wrong-Password-1!", ip, fifth);
        auth.login(user.email(), "Wrong-Password-1!", ip);
        auth.login(user.email(), user.password(), ip);

        List<String> throttled = ownerJdbc()
                .sql("SELECT correlation_id FROM audit_logs WHERE target_id = :id AND action = 'LOGIN_THROTTLED'")
                .param("id", user.id())
                .query(String.class)
                .list();
        assertThat(throttled).containsExactly(fifth);
        assertThat(auditRow(fifth, "LOGIN_THROTTLED"))
                .containsEntry("actor_type", "ANONYMOUS")
                .containsEntry("target_type", "USER")
                .containsEntry("scope", "ACCOUNT_IP")
                .containsEntry("failure_count", 5)
                .containsEntry("blocked_seconds", 30);
    }

    @Test
    void ipScopeThrottleStartAudited() {
        String ip = Fixtures.uniqueIp();
        String twentieth = AuthClient.newRequestId();
        for (int i = 0; i < 19; i++) {
            auth.login(Fixtures.uniqueEmail(), "Wrong-Password-1!", ip);
        }
        auth.login(Fixtures.uniqueEmail(), "Wrong-Password-1!", ip, twentieth);

        assertThat(auditRow(twentieth, "LOGIN_THROTTLED"))
                .containsEntry("scope", "IP")
                .containsEntry("failure_count", 20)
                .containsEntry("blocked_seconds", 30)
                .containsEntry("ip", ip);
    }

    @Test
    void successAudited() {
        TestUser user = fixtures.createUser(RoleCode.VIEWER);
        String ip = Fixtures.uniqueIp();
        String correlationId = AuthClient.newRequestId();

        assertThat(auth.login(user.email(), user.password(), ip, correlationId).status()).isEqualTo(200);

        assertThat(auditRow(correlationId, "LOGIN_SUCCEEDED"))
                .containsEntry("actor_type", "USER")
                .containsEntry("actor_user_id", user.id())
                .containsEntry("actor_email", user.email())
                .containsEntry("target_type", "USER")
                .containsEntry("target_id", user.id())
                .containsEntry("ip", ip);
    }

    @Test
    void ipv6ClientAddress() {
        // Review Focus 4 : IPv6 stockée telle quelle dans l'audit, et la limitation suit cette adresse.
        TestUser user = fixtures.createUser(RoleCode.VIEWER);
        String ip = Fixtures.uniqueIpv6();
        String correlationId = AuthClient.newRequestId();

        auth.login(user.email(), "Wrong-Password-1!", ip, correlationId);
        for (int i = 0; i < 4; i++) {
            auth.login(user.email(), "Wrong-Password-1!", ip);
        }

        assertThat(auditRow(correlationId, "LOGIN_FAILED")).containsEntry("ip", ip);
        assertThat(auth.login(user.email(), user.password(), ip).status()).isEqualTo(429);
        assertThat(auth.login(user.email(), user.password(), Fixtures.uniqueIpv6()).status()).isEqualTo(200);
    }
}
