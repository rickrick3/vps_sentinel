package com.expertiz.sentinel.core.projects.internal;

import java.time.Instant;
import java.util.UUID;

public record EnvironmentResponse(UUID id, UUID projectId, String name, String kind, Instant createdAt, Instant updatedAt,
        long version) {

    static EnvironmentResponse of(Environment environment) {
        return new EnvironmentResponse(environment.getId(), environment.getProjectId(), environment.getName(),
                environment.getKind().name(), environment.getCreatedAt(), environment.getUpdatedAt(), environment.getVersion());
    }
}
