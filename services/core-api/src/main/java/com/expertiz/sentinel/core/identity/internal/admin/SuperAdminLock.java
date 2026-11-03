package com.expertiz.sentinel.core.identity.internal.admin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verrou consultatif des SUPER_ADMIN (spec §7.2) : toute opération qui peut changer leur nombre le prend d'abord, puis
 * compte. Deux opérations concurrentes ne décident donc jamais sur le même compte. Libéré à la fin de la transaction.
 */
@Component
public class SuperAdminLock {

    private final JdbcClient jdbc;

    SuperAdminLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        jdbc.sql("SELECT 1 FROM pg_advisory_xact_lock(hashtext('sentinel.super_admin'))").query(Integer.class).single();
    }
}
