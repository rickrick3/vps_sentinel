package com.expertiz.sentinel.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.CoreApiApplication;
import com.expertiz.sentinel.core.support.SentinelPostgres;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** L'API ne migre jamais le schéma et refuse de démarrer sur une base non conforme aux migrations (spec §6.5). */
class UnmigratedDatabaseIT {

    @Test
    void refusesToStartWhenMigrationsAreMissing() {
        SentinelPostgres database = SentinelPostgres.newIsolated();
        try {
            assertThatThrownBy(() -> start(database))
                    .hasRootCauseInstanceOf(FlywayValidateException.class)
                    .rootCause()
                    .hasMessageContaining("Detected resolved migration not applied to database");
            assertThat(database.ownerJdbc()
                            .sql("SELECT to_regclass('sentinel.flyway_schema_history') IS NULL")
                            .query(Boolean.class)
                            .single())
                    .as("l'API ne doit rien avoir migré")
                    .isTrue();
        } finally {
            database.stop();
        }
    }

    @Test
    void refusesToStartWhenAppliedMigrationWasAltered() {
        SentinelPostgres database = SentinelPostgres.newIsolated();
        try {
            database.migrate();
            database.ownerJdbc()
                    .sql("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'")
                    .update();
            assertThatThrownBy(() -> start(database))
                    .hasRootCauseInstanceOf(FlywayValidateException.class)
                    .rootCause()
                    .hasMessageContaining("checksum mismatch");
        } finally {
            database.stop();
        }
    }

    @Test
    void refusesToStartWhenAMigrationFailed() {
        SentinelPostgres database = SentinelPostgres.newIsolated();
        try {
            database.migrate();
            database.ownerJdbc()
                    .sql("UPDATE flyway_schema_history SET success = false WHERE version = '1'")
                    .update();
            assertThatThrownBy(() -> start(database))
                    .hasRootCauseInstanceOf(FlywayValidateException.class)
                    .rootCause()
                    .hasMessageContaining("Detected failed migration to version 1");
        } finally {
            database.stop();
        }
    }

    // Arguments de ligne de commande : ils priment sur application.yml, contrairement aux propriétés par défaut.
    private static void start(SentinelPostgres database) {
        new SpringApplicationBuilder(CoreApiApplication.class)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + database.appJdbcUrl(),
                        "--spring.datasource.username=" + SentinelPostgres.APP,
                        "--spring.datasource.password=" + SentinelPostgres.APP_PASSWORD)
                .close();
    }
}
