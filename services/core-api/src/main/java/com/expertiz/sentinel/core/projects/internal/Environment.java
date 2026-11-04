package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/** Environnement d'un projet (spec §9.4). Supprimé logiquement, avec suppression physique de ses domaines et déploiements. */
@Entity
@Table(name = "environments")
@SQLRestriction("deleted_at IS NULL")
public class Environment extends AssignedIdEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    protected Environment() {}

    public Environment(UUID projectId, String name, EnvironmentKind kind, Instant now) {
        this.projectId = projectId;
        this.createdAt = now;
        replace(name, kind, now);
    }

    public void replace(String name, EnvironmentKind kind, Instant now) {
        this.name = name;
        this.kind = kind;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** Champs auditables (liste blanche, spec §9.5). */
    public Map<String, Object> auditState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("projectId", projectId.toString());
        state.put("name", name);
        state.put("kind", kind.name());
        return state;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public EnvironmentKind getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
