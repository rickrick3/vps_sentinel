package com.expertiz.sentinel.core.projects.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findByEnvironmentId(UUID environmentId);

    List<Deployment> findByEnvironmentIdIn(Collection<UUID> environmentIds);
}
