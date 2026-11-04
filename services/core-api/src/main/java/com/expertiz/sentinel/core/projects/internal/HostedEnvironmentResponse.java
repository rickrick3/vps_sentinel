package com.expertiz.sentinel.core.projects.internal;

import java.util.UUID;

/** Environnement hébergé par un VPS, avec son projet ({@code GET /api/v1/servers/{id}/deployments}, spec §10.1). */
public record HostedEnvironmentResponse(UUID deploymentId, UUID projectId, String projectName, UUID environmentId,
        String environmentName, String role) {}
