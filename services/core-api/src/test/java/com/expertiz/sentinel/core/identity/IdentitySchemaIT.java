package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.SentinelPostgres;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Contraintes des tables d'identité, de sessions et de limitation, vérifiées avec le rôle de l'API (spec §9.2). Les
 * colonnes d'empreintes et de hash n'acceptent que leur format : une valeur brute (IP, identifiant, jeton, mot de passe)
 * ne peut pas y être stockée.
 */
class IdentitySchemaIT {

    static final String HASH = UserPersistenceIT.HASH;

    private final SentinelPostgres database = SentinelPostgres.shared();

    private UUID userId;

    private JdbcClient app() {
        return database.appJdbc();
    }

    @BeforeEach
    void user() {
        userId = app().sql("""
                        INSERT INTO users (email, full_name, password_hash, status)
                        VALUES (:email, 'Alice', :hash, 'ACTIVE') RETURNING id""")
                .param("email", Fixtures.uniqueEmail())
                .param("hash", HASH)
                .query(UUID.class)
                .single();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Alice@Example.com   | ACTIVE   | {argon2}$argon2id$v=19$x  | ck_users_email_normalized
            ' alice@example.com'| ACTIVE   | {argon2}$argon2id$v=19$x  | ck_users_email_normalized
            alice@example.com   | LOCKED   | {argon2}$argon2id$v=19$x  | ck_users_status
            alice@example.com   | ACTIVE   | Fixture-Password-1!       | ck_users_password_hash
            alice@example.com   | ACTIVE   | {noop}Fixture-Password-1! | ck_users_password_hash
            alice@example.com   | ACTIVE   | {argon2}$argon2i$v=19$x   | ck_users_password_hash
            """)
    void usersRejectInvalidRows(String email, String status, String hash, String constraint) {
        assertThatThrownBy(() -> app().sql("INSERT INTO users (email, full_name, password_hash, status) VALUES (:e, 'A', :h, :s)")
                        .param("e", email)
                        .param("h", hash)
                        .param("s", status)
                        .update())
                .rootCause()
                .hasMessageContaining(constraint);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
            IP         | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | ck_login_throttles_scope
            ACCOUNT_IP | NULL                             | bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | ck_login_throttles_scope
            ACCOUNT    | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | ck_login_throttles_scope
            IP         | NULL                             | 10.0.0.1                         | ck_login_throttles_ip_fp
            IP         | NULL                             | BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB | ck_login_throttles_ip_fp
            ACCOUNT_IP | alice@example.com                | bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb | ck_login_throttles_account_fp
            """)
    void throttlesStoreOnlyFingerprintsInTheirScope(String scope, String accountFp, String ipFp, String constraint) {
        assertThatThrownBy(() -> insertThrottle(scope, accountFp, ipFp))
                .rootCause()
                .hasMessageContaining(constraint);
    }

    @Test
    void throttleKeyIsUniqueEvenWithoutAccount() {
        String ip = Fixtures.uniqueFingerprint();
        insertThrottle("IP", null, ip);

        assertThatThrownBy(() -> insertThrottle("IP", null, ip)).rootCause().hasMessageContaining("uq_login_throttles_key");
    }

    @Test
    void throttleKeyIsPerAccountAndIp() {
        String ip = Fixtures.uniqueFingerprint();
        String account = Fixtures.uniqueFingerprint();
        insertThrottle("ACCOUNT_IP", account, ip);

        assertThatCode(() -> insertThrottle("ACCOUNT_IP", Fixtures.uniqueFingerprint(), ip)).doesNotThrowAnyException();
        assertThatCode(() -> insertThrottle("IP", null, ip)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertThrottle("ACCOUNT_IP", account, ip))
                .rootCause()
                .hasMessageContaining("uq_login_throttles_key");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
            dGhpcy1pcy1hLXJhdy1yZWZyZXNoLXRva2VuLXZhbHVlLTEyMzQ                | NULL     | NULL    | ck_refresh_tokens_token_hash
            ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff  | now()    | EXPIRED | ck_refresh_tokens_revoked_reason
            ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff  | now()    | NULL    | ck_refresh_tokens_revocation
            ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff  | NULL     | LOGOUT  | ck_refresh_tokens_revocation
            """)
    void refreshTokensRejectInvalidRows(String hash, String revokedAt, String reason, String constraint) {
        assertThatThrownBy(() -> insertRefreshToken(userId, hash, revokedAt, reason))
                .rootCause()
                .hasMessageContaining(constraint);
    }

    @Test
    void refreshTokenHashIsUnique() {
        String hash = Fixtures.uniqueFingerprint() + Fixtures.uniqueFingerprint();
        insertRefreshToken(userId, hash, null, null);

        assertThatThrownBy(() -> insertRefreshToken(userId, hash, null, null))
                .rootCause()
                .hasMessageContaining("uq_refresh_tokens_token_hash");
    }

    @Test
    void refreshTokenRequiresExistingUser() {
        assertThatThrownBy(() -> insertRefreshToken(UUID.randomUUID(), Fixtures.uniqueFingerprint() + Fixtures.uniqueFingerprint(), null, null))
                .rootCause()
                .hasMessageContaining("fk_refresh_tokens_user");
    }

    private void insertThrottle(String scope, String accountFp, String ipFp) {
        app().sql("""
                        INSERT INTO login_throttles (scope, account_fp, ip_fp, failure_count, first_failure_at, last_failure_at)
                        VALUES (:scope, :account, :ip, 1, now(), now())""")
                .param("scope", scope)
                .param("account", accountFp)
                .param("ip", ipFp)
                .update();
    }

    private void insertRefreshToken(UUID user, String hash, String revokedAt, String reason) {
        String revoked = revokedAt == null ? "NULL" : revokedAt;
        app().sql("""
                        INSERT INTO refresh_tokens (user_id, family_id, token_hash, session_started_at, issued_at, expires_at,
                                                    revoked_at, revoked_reason)
                        VALUES (:user, :family, :hash, now(), now(), now() + interval '24 hours', %s, :reason)"""
                        .formatted(revoked))
                .param("user", user)
                .param("family", UUID.randomUUID())
                .param("hash", hash)
                .param("reason", reason)
                .update();
    }
}
