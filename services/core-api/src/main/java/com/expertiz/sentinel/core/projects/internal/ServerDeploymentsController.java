package com.expertiz.sentinel.core.projects.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Environnements hébergés par un VPS (spec §7.2, §10.1). Servi par {@code projects}, propriétaire des déploiements : le
 * chemin d'URL n'impose pas le module. Lecture avec {@code project:read}.
 */
@RestController
class ServerDeploymentsController {

    private final DeploymentService deployments;

    ServerDeploymentsController(DeploymentService deployments) {
        this.deployments = deployments;
    }

    @GetMapping("/api/v1/servers/{serverId}/deployments")
    @PreAuthorize(ProjectsController.READ)
    List<HostedEnvironmentResponse> hostedBy(@PathVariable UUID serverId) {
        return deployments.hostedBy(serverId);
    }
}
