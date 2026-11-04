package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.infrastructure.ServerSummary;
import java.time.Instant;
import java.util.UUID;

/** Déploiement, avec le nom et le statut administratif du VPS visé (spec §10.1). */
public record DeploymentResponse(UUID id, UUID environmentId, UUID serverId, String serverName, String serverAdminStatus,
        String role, Instant createdAt) {

    static DeploymentResponse of(Deployment deployment, ServerSummary server) {
        return new DeploymentResponse(deployment.getId(), deployment.getEnvironmentId(), deployment.getServerId(),
                server == null ? null : server.name(), server == null ? null : server.adminStatus().name(), deployment.getRole(),
                deployment.getCreatedAt());
    }
}
