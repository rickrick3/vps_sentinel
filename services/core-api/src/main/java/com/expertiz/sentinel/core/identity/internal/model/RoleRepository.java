package com.expertiz.sentinel.core.identity.internal.model;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Lecture seule : la table {@code roles} n'accepte aucune écriture de l'API. */
public interface RoleRepository extends Repository<Role, UUID> {

    Optional<Role> findByCode(RoleCode code);
}
