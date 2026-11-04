package com.expertiz.sentinel.core.projects.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Environnements non supprimés ({@code @SQLRestriction} de {@link Environment}). Un environnement n'est trouvé que si
 * son projet ne l'est pas non plus (Review Focus 3).
 */
interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    String LIVE = "select e from Environment e where e.id = :id"
            + " and exists (select p.id from Project p where p.id = e.projectId and p.deletedAt is null)";

    @Query(LIVE)
    Optional<Environment> findLive(@Param("id") UUID id);

    /** {@code FOR SHARE} : un domaine ou un déploiement ne peut pas être ajouté pendant que l'environnement est supprimé. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(LIVE)
    Optional<Environment> findLiveForShare(@Param("id") UUID id);

    /** {@code FOR UPDATE} : suppression de l'environnement et de ses enfants. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(LIVE)
    Optional<Environment> findLiveForUpdate(@Param("id") UUID id);

    List<Environment> findByProjectIdOrderByNameAsc(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Environment e where e.projectId = :projectId")
    List<Environment> findByProjectIdForUpdate(@Param("projectId") UUID projectId);

    interface ProjectCount {

        UUID getProjectId();

        long getCount();
    }

    @Query("select e.projectId as projectId, count(e) as count from Environment e where e.projectId in :ids group by e.projectId")
    List<ProjectCount> countByProjectIds(@Param("ids") Collection<UUID> ids);
}
