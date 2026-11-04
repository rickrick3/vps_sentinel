package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Déploiement d'un environnement sur un VPS (spec §9.4). Supprimé physiquement, après un audit avec instantané. */
@Entity
@Table(name = "deployments")
public class Deployment extends AssignedIdEntity {

    @Column(name = "environment_id", nullable = false, updatable = false)
    private UUID environmentId;

    @Column(name = "server_id", nullable = false, updatable = false)
    private UUID serverId;

    @Column(updatable = false)
    private String role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected Deployment() {}

    public Deployment(UUID environmentId, UUID serverId, String role, UUID createdBy, Instant now) {
        this.environmentId = environmentId;
        this.serverId = serverId;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    /** Instantané auditable (spec §9.5). */
    public Map<String, Object> auditState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("environmentId", environmentId.toString());
        state.put("serverId", serverId.toString());
        state.put("role", role);
        return state;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public UUID getServerId() {
        return serverId;
    }

    public String getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
