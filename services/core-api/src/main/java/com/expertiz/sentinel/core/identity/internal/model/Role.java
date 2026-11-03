package com.expertiz.sentinel.core.identity.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Rôle fixe, inséré par migration et en lecture seule pour l'API, jusque dans la base (spec §6.5). */
@Entity
@Table(name = "roles")
@Immutable
public class Role {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleCode code;

    @Column(nullable = false)
    private String label;

    protected Role() {}

    public UUID getId() {
        return id;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
