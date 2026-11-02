package com.expertiz.sentinel.core.shared.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * core-api n'exécute jamais de migration (spec §6.5) : le conteneur {@code migrate} s'en charge avec
 * {@code sentinel_owner}. Au démarrage, Flyway valide seulement, avec {@code sentinel_app} ; une migration manquante,
 * en échec ou modifiée empêche l'API de démarrer.
 */
@Configuration(proxyBeanMethods = false)
public class FlywayValidateOnlyConfig {

    @Bean
    FlywayMigrationStrategy validateOnly() {
        return Flyway::validate;
    }
}
