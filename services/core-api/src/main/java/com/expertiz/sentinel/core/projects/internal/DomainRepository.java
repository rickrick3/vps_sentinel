package com.expertiz.sentinel.core.projects.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DomainRepository extends JpaRepository<Domain, UUID> {

    List<Domain> findByEnvironmentIdOrderByHostnameAsc(UUID environmentId);

    List<Domain> findByEnvironmentIdInOrderByHostnameAsc(Collection<UUID> environmentIds);

    /** L'ancien domaine principal perd ce statut, dans la transaction qui en ajoute un nouveau (spec §10.1). */
    @Modifying(flushAutomatically = true)
    @Query("update Domain d set d.primary = false where d.environmentId = :environmentId and d.primary = true")
    int clearPrimary(@Param("environmentId") UUID environmentId);
}
