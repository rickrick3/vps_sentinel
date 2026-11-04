package com.expertiz.sentinel.core.support;

/**
 * Application complète sur une base neuve, réservée à une classe de test : pour les tests qui supposent un état global
 * (aucun SUPER_ADMIN, premier administrateur). Chaque sous-classe déclare sa base et la branche, ce qui lui donne aussi
 * son propre contexte Spring :
 *
 * <pre>
 * static final SentinelPostgres DATABASE = IsolatedDatabaseIT.newDatabase();
 *
 * &#64;DynamicPropertySource
 * static void database(DynamicPropertyRegistry registry) { connect(registry, DATABASE); }
 *
 * &#64;Override protected SentinelPostgres database() { return DATABASE; }
 * </pre>
 */
public abstract class IsolatedDatabaseIT extends ApplicationTestBase {

    /** Nouveau PostgreSQL, initialisé et migré comme la base partagée. Testcontainers l'arrête en fin de JVM. */
    protected static SentinelPostgres newDatabase() {
        SentinelPostgres database = SentinelPostgres.newIsolated();
        database.migrate();
        return database;
    }
}
