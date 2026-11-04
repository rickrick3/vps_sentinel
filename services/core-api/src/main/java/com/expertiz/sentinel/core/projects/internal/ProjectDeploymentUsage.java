package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.infrastructure.ServerUsageChecker;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Réponse de {@code projects} à {@code infrastructure} : un VPS est utilisé tant qu'un déploiement le vise (spec §7.2).
 * Appelée sous le verrou {@code FOR UPDATE} du VPS ; les déploiements d'un environnement supprimé ont été supprimés
 * avec lui.
 */
@Component
class ProjectDeploymentUsage implements ServerUsageChecker {

    private final JdbcClient jdbc;

    ProjectDeploymentUsage(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasDeployments(UUID serverId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM deployments WHERE server_id = :server)")
                .param("server", serverId)
                .query(Boolean.class)
                .single();
    }
}
