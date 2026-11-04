package com.expertiz.sentinel.core.projects.internal;

import java.time.Instant;
import java.util.UUID;

/** Projet dans une liste, avec le nombre de ses environnements (spec §10.1). */
public record ProjectResponse(UUID id, String name, String description, String repositoryUrl, long environmentCount,
        Instant createdAt, Instant updatedAt, long version) {

    static ProjectResponse of(Project project, long environmentCount) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getRepositoryUrl(),
                environmentCount, project.getCreatedAt(), project.getUpdatedAt(), project.getVersion());
    }
}
