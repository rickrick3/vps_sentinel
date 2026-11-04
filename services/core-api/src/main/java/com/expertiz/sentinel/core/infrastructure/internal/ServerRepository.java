package com.expertiz.sentinel.core.infrastructure.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** VPS non supprimés ({@code @SQLRestriction} de {@link Server}). */
interface ServerRepository extends JpaRepository<Server, UUID>, JpaSpecificationExecutor<Server> {

    /** {@code SELECT … FOR UPDATE} : à prendre avant de vérifier qu'aucun déploiement n'utilise le VPS (spec §7.2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Server s where s.id = :id")
    Optional<Server> findForUpdate(@Param("id") UUID id);

    @Query("select distinct s.provider from Server s where s.provider is not null order by s.provider")
    List<String> providers();

    @Query("select distinct s.region from Server s where s.region is not null order by s.region")
    List<String> regions();

    @Query("select distinct t from Server s join s.tags t order by t")
    List<String> tags();
}
