package com.expertiz.sentinel.core.identity.internal.model;

import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;

/** Utilisateur (spec §9.2). Supprimé logiquement : un utilisateur supprimé n'est plus jamais chargé. */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User extends AssignedIdEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_expires_at")
    private Instant passwordExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    // Mis à jour par une requête dédiée, sans incrément de version (spec §8.4) : l'entité ne l'écrit jamais.
    @Column(name = "last_login_at", insertable = false, updatable = false)
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    @ManyToOne(optional = false)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Role role;

    protected User() {}

    /** Nouvel utilisateur actif. L'email est stocké sous sa forme normalisée (spec §10.3). */
    public User(String email, String fullName, String passwordHash, Role role, boolean mustChangePassword,
            Instant passwordExpiresAt, Instant now) {
        this.email = Fingerprints.normalizeIdentifier(email);
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.mustChangePassword = mustChangePassword;
        this.passwordExpiresAt = passwordExpiresAt;
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Champs auditables (liste blanche, spec §9.5) : jamais le hash ni les dates techniques. */
    public Map<String, Object> auditState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("email", email);
        state.put("fullName", fullName);
        state.put("role", role.getCode().name());
        state.put("status", status.name());
        state.put("mustChangePassword", mustChangePassword);
        return state;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getPasswordExpiresAt() {
        return passwordExpiresAt;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Long getVersion() {
        return version;
    }

    public Role getRole() {
        return role;
    }
}
