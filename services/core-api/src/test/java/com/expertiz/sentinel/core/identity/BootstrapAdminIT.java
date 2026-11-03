package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.identity.internal.admin.SuperAdminLock;
import com.expertiz.sentinel.core.identity.internal.bootstrap.BootstrapAdminRunner;
import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.support.AuthClient.Response;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.IsolatedDatabaseIT;
import com.expertiz.sentinel.core.support.SentinelPostgres;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * Premier administrateur (spec §8.7), sur une base propre à la classe, vidée de ses utilisateurs avant chaque test.
 * Aucun cas d'erreur ne bloque le démarrage, et le mot de passe n'apparaît jamais dans les logs.
 */
@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminIT extends IsolatedDatabaseIT {

    static final SentinelPostgres DATABASE = newDatabase();
    static final String PASSWORD = "Bootstrap-Password-1!";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        connect(registry, DATABASE);
    }

    @Override
    protected SentinelPostgres database() {
        return DATABASE;
    }

    @Autowired
    BootstrapAdminRunner runner;

    @Autowired
    SuperAdminLock lock;

    @BeforeEach
    void noUsers() {
        ownerJdbc().sql("DELETE FROM refresh_tokens").update();
        ownerJdbc().sql("DELETE FROM login_throttles").update();
        ownerJdbc().sql("DELETE FROM user_roles").update();
        ownerJdbc().sql("DELETE FROM users").update();
    }

    private List<Map<String, Object>> users() {
        return ownerJdbc()
                .sql("""
                        SELECT u.id, u.email, u.full_name, u.status, u.must_change_password, u.password_expires_at, r.code AS role
                          FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id""")
                .query()
                .listOfRows();
    }

    @Test
    void createsWhenNoActiveSuperAdmin() {
        runner.bootstrap("admin@sentinel.test", PASSWORD, "Équipe infra");

        assertThat(users()).singleElement().satisfies(admin -> assertThat(admin)
                .containsEntry("email", "admin@sentinel.test")
                .containsEntry("full_name", "Équipe infra")
                .containsEntry("role", "SUPER_ADMIN")
                .containsEntry("status", "ACTIVE")
                .containsEntry("must_change_password", true)
                .containsEntry("password_expires_at", null));
        Map<String, Object> audit = ownerJdbc()
                .sql("""
                        SELECT actor_type, actor_user_id, target_type, details ->> 'role' AS role, details ->> 'email' AS email
                          FROM audit_logs WHERE action = 'USER_CREATED' AND target_id = :id""")
                .param("id", users().getFirst().get("id"))
                .query()
                .singleRow();
        assertThat(audit)
                .containsEntry("actor_type", "SYSTEM")
                .containsEntry("actor_user_id", null)
                .containsEntry("target_type", "USER")
                .containsEntry("role", "SUPER_ADMIN")
                .containsEntry("email", "admin@sentinel.test");
        assertThat(ownerJdbc().sql("SELECT string_agg(a::text, '|') FROM audit_logs a").query(String.class).single())
                .doesNotContain(PASSWORD);
    }

    @Test
    void firstLoginForcesAPasswordChange() {
        runner.bootstrap("admin@sentinel.test", PASSWORD, "Administrateur");

        Response login = auth.login("admin@sentinel.test", PASSWORD, Fixtures.uniqueIp());

        assertThat(login.status()).isEqualTo(200);
        assertThat((Boolean) login.json("$.user.mustChangePassword")).isTrue();
        assertThat((String) login.json("$.user.role")).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void ignoredWhenActiveSuperAdminExists(CapturedOutput output) {
        fixtures.createUser(RoleCode.SUPER_ADMIN);

        runner.bootstrap("admin@sentinel.test", PASSWORD, "Administrateur");

        assertThat(users()).singleElement().satisfies(u -> assertThat(u.get("email")).isNotEqualTo("admin@sentinel.test"));
        // Cas normal après la première connexion : ignoré sans erreur.
        assertThat(output.getAll()).doesNotContain("aucun administrateur créé");
    }

    @Test
    void disabledSuperAdminDoesNotCount() {
        var existing = fixtures.createUser(RoleCode.SUPER_ADMIN);
        appJdbc().sql("UPDATE users SET status = 'DISABLED' WHERE id = :id").param("id", existing.id()).update();

        runner.bootstrap("admin@sentinel.test", PASSWORD, "Administrateur");

        assertThat(users()).extracting(u -> u.get("email")).contains("admin@sentinel.test");
    }

    @Test
    void noopWhenVariablesMissing() {
        runner.bootstrap(null, null, null);
        runner.bootstrap("", PASSWORD, "Administrateur");
        runner.bootstrap("   ", PASSWORD, "Administrateur");
        runner.bootstrap("admin@sentinel.test", null, "Administrateur");
        runner.bootstrap("admin@sentinel.test", "", "Administrateur");

        assertThat(users()).isEmpty();
    }

    @Test
    void weakPasswordRefusedAndNeverLogged(CapturedOutput output) {
        runner.bootstrap("admin@sentinel.test", "Short-Pw-1!", "Administrateur");

        assertThat(users()).isEmpty();
        assertThat(output.getAll()).contains("SENTINEL_BOOTSTRAP_ADMIN_PASSWORD").doesNotContain("Short-Pw-1!");
    }

    @Test
    void passwordEqualToTheEmailRefused() {
        runner.bootstrap("admin.sentinel@sentinel.test", "Admin.Sentinel@Sentinel.test", "Administrateur");

        assertThat(users()).isEmpty();
    }

    @Test
    void invalidEmailRefused(CapturedOutput output) {
        runner.bootstrap("admin", PASSWORD, "Administrateur");
        runner.bootstrap("admin@localhost", PASSWORD, "Administrateur");

        assertThat(users()).isEmpty();
        assertThat(output.getAll()).contains("SENTINEL_BOOTSTRAP_ADMIN_EMAIL");
    }

    @Test
    void existingEmailNeverPromotedNorReactivated(CapturedOutput output) {
        var developer = fixtures.createUser(RoleCode.DEVELOPER);
        appJdbc().sql("UPDATE users SET status = 'DISABLED' WHERE id = :id").param("id", developer.id()).update();

        runner.bootstrap(developer.email().toUpperCase(), PASSWORD, "Administrateur");

        assertThat(users()).singleElement().satisfies(u -> assertThat(u)
                .containsEntry("role", "DEVELOPER")
                .containsEntry("status", "DISABLED"));
        assertThat(output.getAll()).contains("SENTINEL_BOOTSTRAP_ADMIN_EMAIL").doesNotContain(developer.email());
    }

    @Test
    void emailNormalized() {
        // Review Focus 5 : email avec majuscules et espaces, connexion en minuscules.
        runner.bootstrap("  Admin@Sentinel.LOCAL ", PASSWORD, "Administrateur");

        assertThat(users()).singleElement().satisfies(u -> assertThat(u).containsEntry("email", "admin@sentinel.local"));
        assertThat(auth.login("admin@sentinel.local", PASSWORD, Fixtures.uniqueIp()).status()).isEqualTo(200);
    }

    @Test
    void defaultNameWhenMissing() {
        runner.bootstrap("admin@sentinel.test", PASSWORD, "  ");

        assertThat(users()).singleElement().satisfies(u -> assertThat(u).containsEntry("full_name", "Administrateur"));
    }

    @Test
    void overlongNameRefused() {
        runner.bootstrap("admin@sentinel.test", PASSWORD, "x".repeat(121));

        assertThat(users()).isEmpty();
    }

    @Test
    void concurrentBootstrapsCreateASingleAdmin() throws Exception {
        // Sans le verrou consultatif, les deux transactions verraient zéro SUPER_ADMIN et en créeraient deux.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> runs = List.of(
                    pool.submit(() -> {
                        start.await();
                        runner.bootstrap("first@sentinel.test", PASSWORD, "Premier");
                        return null;
                    }),
                    pool.submit(() -> {
                        start.await();
                        runner.bootstrap("second@sentinel.test", PASSWORD, "Second");
                        return null;
                    }));
            start.countDown();
            for (Future<?> run : runs) {
                run.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(users()).singleElement().satisfies(u -> assertThat(u).containsEntry("role", "SUPER_ADMIN"));
    }

    @Test
    void lockRequiresATransaction() {
        assertThatThrownBy(lock::acquire).isInstanceOf(IllegalTransactionStateException.class);
    }
}
