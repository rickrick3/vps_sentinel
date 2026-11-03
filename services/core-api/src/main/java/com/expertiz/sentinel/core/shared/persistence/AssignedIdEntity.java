package com.expertiz.sentinel.core.shared.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Agrégat dont l'id UUIDv7 est attribué par l'application à la construction (spec §9.1) : il est connu avant
 * l'insertion. {@link #isNew()} indique à Spring Data s'il faut insérer ({@code persist}) ou fusionner.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected AssignedIdEntity() {
        this.id = UuidV7.next();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        isNew = false;
    }
}
