package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.SQLRestriction;

/** Projet (spec §9.4). Supprimé logiquement : un projet supprimé n'est plus jamais chargé, ni ses environnements. */
@Entity
@Table(name = "projects")
@SQLRestriction("deleted_at IS NULL")
public class Project extends AssignedIdEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "repository_url")
    private String repositoryUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    protected Project() {}

    public Project(String name, String description, String repositoryUrl, Instant now) {
        replace(name, description, repositoryUrl, now);
        this.createdAt = now;
    }

    public void replace(String name, String description, String repositoryUrl, Instant now) {
        this.name = name;
        this.description = description;
        this.repositoryUrl = repositoryUrl;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** Champs auditables (liste blanche, spec §9.5). */
    public Map<String, Object> auditState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", name);
        state.put("description", description);
        state.put("repositoryUrl", repositoryUrl);
        return state;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
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
