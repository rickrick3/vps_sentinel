package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.audit.AuditDiff;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Environnements (spec §9.4, §9.6). Un environnement dont le projet est supprimé est introuvable (Review Focus 3). La
 * création verrouille le projet en partage, la suppression verrouille l'environnement : rien ne s'ajoute à un parent en
 * cours de suppression.
 */
@Service
class EnvironmentService {

    static final String TARGET = "ENVIRONMENT";

    private final ProjectRepository projects;
    private final EnvironmentRepository environments;
    private final DomainRepository domains;
    private final DeploymentRepository deployments;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    EnvironmentService(ProjectRepository projects, EnvironmentRepository environments, DomainRepository domains,
            DeploymentRepository deployments, ApplicationEventPublisher events, Clock clock) {
        this.projects = projects;
        this.environments = environments;
        this.domains = domains;
        this.deployments = deployments;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public EnvironmentResponse create(UUID projectId, EnvironmentRequest request) {
        projects.findForShare(projectId).orElseThrow(ProjectService::notFound);
        Environment environment = environments.saveAndFlush(
                new Environment(projectId, request.name(), EnvironmentKind.valueOf(request.kind()), clock.instant()));
        events.publishEvent(AuditEvent.of("ENVIRONMENT_CREATED", TARGET, environment.getId(), environment.auditState()));
        return EnvironmentResponse.of(environment);
    }

    @Transactional
    public EnvironmentResponse replace(UUID id, EnvironmentRequest request) {
        Environment environment = environments.findLive(id).orElseThrow(EnvironmentService::notFound);
        if (!environment.getVersion().equals(request.version())) {
            throw new ApiException(ProblemType.VERSION_CONFLICT, "Cet élément a été modifié par quelqu'un d'autre.");
        }
        Map<String, Object> before = environment.auditState();
        environment.replace(request.name(), EnvironmentKind.valueOf(request.kind()), clock.instant());
        environments.flush();
        Map<String, Map<String, Object>> changes = AuditDiff.changes(before, environment.auditState());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("ENVIRONMENT_UPDATED", TARGET, id, Map.of("changes", changes)));
        }
        return EnvironmentResponse.of(environment);
    }

    @Transactional
    public void delete(UUID id) {
        softDeleteWithChildren(environments.findLiveForUpdate(id).orElseThrow(EnvironmentService::notFound));
    }

    /**
     * Suppression de l'environnement, déjà verrouillé par l'appelant ({@code FOR UPDATE}) : ses domaines et ses
     * déploiements sont supprimés physiquement, lui logiquement ; un audit avec instantané par élément (spec §9.6).
     * Verrou pris avant la lecture des enfants : aucun ajout concurrent ne peut leur échapper.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void softDeleteWithChildren(Environment environment) {
        for (Domain domain : domains.findByEnvironmentIdOrderByHostnameAsc(environment.getId())) {
            domains.delete(domain);
            events.publishEvent(AuditEvent.of("DOMAIN_REMOVED", DomainService.TARGET, domain.getId(), domain.auditState()));
        }
        for (Deployment deployment : deployments.findByEnvironmentId(environment.getId())) {
            deployments.delete(deployment);
            events.publishEvent(AuditEvent.of("DEPLOYMENT_REMOVED", DeploymentService.TARGET, deployment.getId(), deployment.auditState()));
        }
        Map<String, Object> snapshot = environment.auditState();
        environment.delete(clock.instant());
        environments.flush();
        events.publishEvent(AuditEvent.of("ENVIRONMENT_DELETED", TARGET, environment.getId(), snapshot));
    }

    static ApiException notFound() {
        return new ApiException(ProblemType.NOT_FOUND, "Environnement introuvable.");
    }
}
