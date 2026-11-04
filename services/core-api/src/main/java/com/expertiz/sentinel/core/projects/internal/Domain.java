package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Domaine d'un environnement (spec §9.4). Supprimé physiquement, après un audit avec instantané. */
@Entity
@Table(name = "domains")
public class Domain extends AssignedIdEntity {

    @Column(name = "environment_id", nullable = false, updatable = false)
    private UUID environmentId;

    @Column(nullable = false, updatable = false)
    private String hostname;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Domain() {}

    public Domain(UUID environmentId, String hostname, boolean primary, Instant now) {
        this.environmentId = environmentId;
        this.hostname = hostname;
        this.primary = primary;
        this.createdAt = now;
    }

    /** Instantané auditable (spec §9.5). */
    public Map<String, Object> auditState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("environmentId", environmentId.toString());
        state.put("hostname", hostname);
        state.put("isPrimary", primary);
        return state;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public String getHostname() {
        return hostname;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
