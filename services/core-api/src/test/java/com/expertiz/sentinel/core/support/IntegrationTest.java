package com.expertiz.sentinel.core.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Application complète sur la base partagée, démarrée et migrée une seule fois par JVM. */
public abstract class IntegrationTest extends ApplicationTestBase {

    protected static final SentinelPostgres DATABASE = SentinelPostgres.shared();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        connect(registry, DATABASE);
    }

    @Override
    protected SentinelPostgres database() {
        return DATABASE;
    }
}
