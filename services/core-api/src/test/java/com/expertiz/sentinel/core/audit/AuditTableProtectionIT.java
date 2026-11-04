package com.expertiz.sentinel.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.support.SentinelPostgres;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Journal d'audit en ajout seul, imposé par la base (spec §6.5, §9.5 et §13.5). */
class AuditTableProtectionIT {

    private final SentinelPostgres database = SentinelPostgres.shared();

    private UUID insertRow(JdbcClient jdbc) {
        return jdbc.sql("INSERT INTO audit_logs (actor_type, action) VALUES ('SYSTEM', 'TEST_PROTECTION') RETURNING id")
                .query(UUID.class)
                .single();
    }

    @Test
    void appRoleCanInsertAndRead() {
        UUID id = insertRow(database.appJdbc());

        assertThat(database.appJdbc().sql("SELECT action FROM audit_logs WHERE id = :id").param("id", id).query(String.class).single())
                .isEqualTo("TEST_PROTECTION");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE audit_logs SET action = 'TAMPERED' WHERE id = :id",
        "DELETE FROM audit_logs WHERE id = :id"
    })
    void ownerRowChangesRejectedByTrigger(String sql) {
        UUID id = insertRow(database.ownerJdbc());

        assertThatThrownBy(() -> database.ownerJdbc().sql(sql).param("id", id).update())
                .rootCause()
                .hasMessageContaining("audit_logs is append-only");
    }

    @Test
    void ownerTruncateRejectedByTrigger() {
        insertRow(database.ownerJdbc());

        assertThatThrownBy(() -> database.ownerJdbc().sql("TRUNCATE audit_logs").update())
                .rootCause()
                .hasMessageContaining("audit_logs is append-only");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE audit_logs SET action = 'TAMPERED' WHERE id = :id",
        "DELETE FROM audit_logs WHERE id = :id",
        "TRUNCATE audit_logs"
    })
    void appRoleHasNoUpdateDeleteTruncate(String sql) {
        UUID id = insertRow(database.appJdbc());

        assertThatThrownBy(() -> database.appJdbc().sql(sql).param("id", id).update())
                .rootCause()
                .hasMessageContaining("permission denied for table audit_logs");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ALTER TABLE audit_logs ADD COLUMN x int",
        "ALTER TABLE audit_logs DISABLE TRIGGER ALL",
        "DROP TABLE audit_logs"
    })
    void appRoleCannotAlterDisableTriggersOrDrop(String sql) {
        assertThatThrownBy(() -> database.appJdbc().sql(sql).update())
                .rootCause()
                .hasMessageContaining("must be owner of table audit_logs");
    }
}
