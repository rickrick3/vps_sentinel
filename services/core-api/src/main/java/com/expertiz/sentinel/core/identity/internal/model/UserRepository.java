package com.expertiz.sentinel.core.identity.internal.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Utilisateurs non supprimés ({@code @SQLRestriction} de {@link User}). */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Recherche par email déjà normalisé (spec §10.3). */
    Optional<User> findByEmail(String email);

    /** À appeler sous le verrou des SUPER_ADMIN quand le résultat décide d'une écriture (spec §7.2). */
    long countByRoleCodeAndStatus(RoleCode role, UserStatus status);

    /** Idem, sans la cible de l'opération : reste-t-il un autre SUPER_ADMIN actif ? */
    long countByRoleCodeAndStatusAndIdNot(RoleCode role, UserStatus status, UUID excluded);

    /** Connexion réussie : mise à jour directe, sans incrément de {@code version} (spec §8.4). */
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "UPDATE users SET last_login_at = :at WHERE id = :id", nativeQuery = true)
    int touchLastLogin(@Param("id") UUID id, @Param("at") Instant at);
}
