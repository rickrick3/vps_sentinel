package com.expertiz.sentinel.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.support.SentinelPostgres;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Droits du rôle applicatif sur PostgreSQL réel (spec §6.5 et §13.5). */
class DatabasePrivilegesIT {

    private final SentinelPostgres database = SentinelPostgres.shared();

    private JdbcClient app() {
        return database.appJdbc();
    }

    @Test
    void appRoleCannotCreateTables() {
        assertThatThrownBy(() -> app().sql("CREATE TABLE x(id int)").update())
                .rootCause()
                .hasMessageContaining("permission denied for schema sentinel");
    }

    @Test
    void appRoleCannotUsePublicSchema() {
        assertThatThrownBy(() -> app().sql("CREATE TABLE public.x(id int)").update())
                .rootCause()
                .hasMessageContaining("permission denied for schema public");
    }

    @Test
    void appRoleCannotCreateTemporaryTables() {
        assertThatThrownBy(() -> app().sql("CREATE TEMP TABLE x(id int)").update())
                .rootCause()
                .hasMessageContaining("permission denied to create temporary tables");
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgres", "template1"})
    void appRoleCannotConnectToOtherDatabases(String other) {
        var dataSource = new DriverManagerDataSource(database.jdbcUrl(other), SentinelPostgres.APP, SentinelPostgres.APP_PASSWORD);
        assertThatThrownBy(() -> {
                    try (Connection ignored = dataSource.getConnection()) {
                        // la connexion doit être refusée
                    }
                })
                .hasMessageContaining("permission denied for database \"" + other + "\"");
    }

    @Test
    void appRoleReadsFlywayHistory() {
        assertThat(app().sql("SELECT count(*) FROM flyway_schema_history").query(Long.class).single())
                .isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "INSERT INTO flyway_schema_history (installed_rank, description, type, script, installed_by, execution_time, success)"
                + " VALUES (99, 'x', 'SQL', 'x', 'x', 0, true)",
        "UPDATE flyway_schema_history SET success = true",
        "DELETE FROM flyway_schema_history"
    })
    void appRoleCannotWriteFlywayHistory(String sql) {
        assertThatThrownBy(() -> app().sql(sql).update())
                .rootCause()
                .hasMessageContaining("permission denied for table flyway_schema_history");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALTER TABLE flyway_schema_history ADD COLUMN x int", "DROP TABLE flyway_schema_history"})
    void appRoleCannotAlterOrDropTables(String sql) {
        assertThatThrownBy(() -> app().sql(sql).update())
                .rootCause()
                .hasMessageContaining("must be owner of table flyway_schema_history");
    }
}
