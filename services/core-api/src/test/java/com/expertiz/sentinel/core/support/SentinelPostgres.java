package com.expertiz.sentinel.core.support;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL 18 réel, initialisé par le script de la prod (spec §13.1) : rôles {@code sentinel_owner} et
 * {@code sentinel_app}, base et schéma {@code sentinel}. Les migrations s'appliquent avec le rôle propriétaire.
 */
public final class SentinelPostgres {

    public static final String DATABASE = "sentinel";
    public static final String OWNER = "sentinel_owner";
    public static final String OWNER_PASSWORD = "owner-test";
    public static final String APP = "sentinel_app";
    public static final String APP_PASSWORD = "app-test";

    static final Path INIT_SCRIPT =
            Path.of("../../infrastructure/postgres/init/01-roles.sh").toAbsolutePath().normalize();

    // Mode imposé : sous Windows, celui du fichier hôte n'est pas fiable, et l'entrypoint de l'image postgres
    // source un script non exécutable au lieu de l'exécuter.
    private static final int INIT_SCRIPT_MODE = 0755;

    private static SentinelPostgres shared;

    private final PostgreSQLContainer container;

    private SentinelPostgres() {
        if (!Files.isRegularFile(INIT_SCRIPT)) {
            throw new IllegalStateException("Script d'initialisation introuvable : " + INIT_SCRIPT);
        }
        container = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
                .withEnv("SENTINEL_DB_OWNER_PASSWORD", OWNER_PASSWORD)
                .withEnv("SENTINEL_DB_APP_PASSWORD", APP_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(INIT_SCRIPT, INIT_SCRIPT_MODE),
                        "/docker-entrypoint-initdb.d/01-roles.sh");
        container.start();
    }

    /** Base partagée par les tests d'intégration, migrée une seule fois par JVM. */
    public static synchronized SentinelPostgres shared() {
        if (shared == null) {
            shared = new SentinelPostgres();
            shared.migrate();
        }
        return shared;
    }

    /** Base neuve et non migrée, à arrêter par l'appelant. */
    public static SentinelPostgres newIsolated() {
        return new SentinelPostgres();
    }

    public String jdbcUrl(String database) {
        return "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/" + database;
    }

    public String appJdbcUrl() {
        return jdbcUrl(DATABASE);
    }

    public JdbcClient ownerJdbc() {
        return JdbcClient.create(new DriverManagerDataSource(appJdbcUrl(), OWNER, OWNER_PASSWORD));
    }

    public JdbcClient appJdbc() {
        return JdbcClient.create(new DriverManagerDataSource(appJdbcUrl(), APP, APP_PASSWORD));
    }

    public void migrate() {
        Flyway.configure()
                .dataSource(appJdbcUrl(), OWNER, OWNER_PASSWORD)
                .schemas(DATABASE)
                .createSchemas(false)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public String logs() {
        return container.getLogs();
    }

    public void stop() {
        container.stop();
    }
}
