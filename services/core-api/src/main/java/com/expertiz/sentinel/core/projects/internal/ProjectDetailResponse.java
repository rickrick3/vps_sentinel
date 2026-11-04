package com.expertiz.sentinel.core.projects.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fiche d'un projet (spec §10.1) : ses environnements triés par nom, chacun avec ses domaines et ses déploiements (nom et
 * statut du VPS).
 */
public record ProjectDetailResponse(UUID id, String name, String description, String repositoryUrl,
        List<EnvironmentDetail> environments, Instant createdAt, Instant updatedAt, long version) {

    public record EnvironmentDetail(UUID id, String name, String kind, List<DomainResponse> domains,
            List<DeploymentResponse> deployments, Instant createdAt, Instant updatedAt, long version) {}

    static ProjectDetailResponse of(Project project, List<EnvironmentDetail> environments) {
        return new ProjectDetailResponse(project.getId(), project.getName(), project.getDescription(), project.getRepositoryUrl(),
                environments, project.getCreatedAt(), project.getUpdatedAt(), project.getVersion());
    }
}
