package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.infrastructure.ServerCatalog;
import com.expertiz.sentinel.core.infrastructure.ServerSummary;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Déploiements (spec §7.2, §9.4). Ordre « verrou, puis vérification, puis décision » : l'environnement est verrouillé en
 * partage (sa suppression attend), puis le VPS via {@link ServerCatalog#lockForDeployment} (sa suppression ou son
 * décommissionnement attend, ou bien l'a déjà emporté et le déploiement est refusé), puis le déploiement est inséré.
 */
@Service
class DeploymentService {

    static final String TARGET = "DEPLOYMENT";

    private final EnvironmentRepository environments;
    private final DeploymentRepository deployments;
    private final ServerCatalog servers;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DeploymentService(EnvironmentRepository environments, DeploymentRepository deployments, ServerCatalog servers, JdbcClient jdbc,
            ApplicationEventPublisher events, Clock clock) {
        this.environments = environments;
        this.deployments = deployments;
        this.servers = servers;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public DeploymentResponse attach(UUID environmentId, DeploymentRequest request, UUID actorId) {
        environments.findLiveForShare(environmentId).orElseThrow(EnvironmentService::notFound);
        ServerSummary server = servers.lockForDeployment(request.serverId());
        Deployment deployment = deployments.saveAndFlush(
                new Deployment(environmentId, server.id(), request.role(), actorId, clock.instant()));
        events.publishEvent(AuditEvent.of("DEPLOYMENT_ADDED", TARGET, deployment.getId(), deployment.auditState()));
        return DeploymentResponse.of(deployment, server);
    }

    @Transactional
    public void detach(UUID id) {
        Deployment deployment = deployments.findById(id).orElseThrow(DeploymentService::notFound);
        environments.findLiveForShare(deployment.getEnvironmentId()).orElseThrow(DeploymentService::notFound);
        deployments.delete(deployment);
        deployments.flush();
        events.publishEvent(AuditEvent.of("DEPLOYMENT_REMOVED", TARGET, id, deployment.auditState()));
    }

    /** Environnements hébergés par un VPS non supprimé ; 404 sinon. Les déploiements n'existent que pour des environnements actifs. */
    @Transactional(readOnly = true)
    public List<HostedEnvironmentResponse> hostedBy(UUID serverId) {
        if (servers.summaries(List.of(serverId)).isEmpty()) {
            throw new ApiException(ProblemType.NOT_FOUND, "VPS introuvable.");
        }
        return jdbc.sql("""
                        SELECT d.id AS deployment_id, p.id AS project_id, p.name AS project_name,
                               e.id AS environment_id, e.name AS environment_name, d.role
                          FROM deployments d
                          JOIN environments e ON e.id = d.environment_id AND e.deleted_at IS NULL
                          JOIN projects p ON p.id = e.project_id AND p.deleted_at IS NULL
                         WHERE d.server_id = :server
                         ORDER BY lower(p.name), lower(e.name)""")
                .param("server", serverId)
                .query((rs, n) -> new HostedEnvironmentResponse(rs.getObject("deployment_id", UUID.class),
                        rs.getObject("project_id", UUID.class), rs.getString("project_name"),
                        rs.getObject("environment_id", UUID.class), rs.getString("environment_name"), rs.getString("role")))
                .list();
    }

    static ApiException notFound() {
        return new ApiException(ProblemType.NOT_FOUND, "Déploiement introuvable.");
    }
}
