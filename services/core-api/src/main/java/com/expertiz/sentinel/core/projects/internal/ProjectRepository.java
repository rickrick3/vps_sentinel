package com.expertiz.sentinel.core.projects.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Projets non supprimés ({@code @SQLRestriction} de {@link Project}). */
interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    /** {@code FOR SHARE} : un environnement ne peut pas être créé pendant que son projet est supprimé. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findForShare(@Param("id") UUID id);

    /** {@code FOR UPDATE} : suppression d'un projet et de ses environnements. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findForUpdate(@Param("id") UUID id);
}
