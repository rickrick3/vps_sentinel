package com.expertiz.sentinel.core.projects.internal;

import java.time.Instant;
import java.util.UUID;

public record DomainResponse(UUID id, String hostname, boolean isPrimary, Instant createdAt) {

    static DomainResponse of(Domain domain) {
        return new DomainResponse(domain.getId(), domain.getHostname(), domain.isPrimary(), domain.getCreatedAt());
    }
}
