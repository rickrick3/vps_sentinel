package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.infrastructure.ServerCatalog;
import com.expertiz.sentinel.core.infrastructure.ServerSummary;
import com.expertiz.sentinel.core.projects.internal.ProjectDetailResponse.EnvironmentDetail;
import com.expertiz.sentinel.core.shared.audit.AuditDiff;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.text.TextRules;
import com.expertiz.sentinel.core.shared.web.PageRequests;
import com.expertiz.sentinel.core.shared.web.PageResponse;
import com.expertiz.sentinel.core.shared.web.SortSpec;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projets (spec §9.4, §10.1). La suppression verrouille le projet puis chacun de ses environnements, et supprime chaque
 * environnement avec ses enfants, dans une seule transaction (spec §9.6).
 */
@Service
class ProjectService {

    static final SortSpec SORT = new SortSpec(Map.of("name", "name", "createdAt", "createdAt"), "name,asc");

    private static final String TARGET = "PROJECT";
    private static final int QUERY_MAX = 254;

    private final ProjectRepository projects;
    private final EnvironmentRepository environments;
    private final DomainRepository domains;
    private final DeploymentRepository deployments;
    private final EnvironmentService environmentService;
    private final ServerCatalog servers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ProjectService(ProjectRepository projects, EnvironmentRepository environments, DomainRepository domains,
            DeploymentRepository deployments, EnvironmentService environmentService, ServerCatalog servers,
            ApplicationEventPublisher events, Clock clock) {
        this.projects = projects;
        this.environments = environments;
        this.domains = domains;
        this.deployments = deployments;
        this.environmentService = environmentService;
        this.servers = servers;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ProjectDetailResponse create(ProjectRequest request) {
        Project project = projects.saveAndFlush(
                new Project(request.name(), request.description(), request.repositoryUrl(), clock.instant()));
        events.publishEvent(AuditEvent.of("PROJECT_CREATED", TARGET, project.getId(), project.auditState()));
        return ProjectDetailResponse.of(project, List.of());
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse get(UUID id) {
        Project project = find(id);
        List<Environment> envs = environments.findByProjectIdOrderByNameAsc(id);
        List<UUID> envIds = envs.stream().map(Environment::getId).toList();
        Map<UUID, List<Domain>> domainsByEnv = envIds.isEmpty() ? Map.of()
                : domains.findByEnvironmentIdInOrderByHostnameAsc(envIds).stream().collect(Collectors.groupingBy(Domain::getEnvironmentId));
        Map<UUID, List<Deployment>> deploymentsByEnv = envIds.isEmpty() ? Map.of()
                : deployments.findByEnvironmentIdIn(envIds).stream().collect(Collectors.groupingBy(Deployment::getEnvironmentId));
        Map<UUID, ServerSummary> servers = this.servers.summaries(
                deploymentsByEnv.values().stream().flatMap(List::stream).map(Deployment::getServerId).distinct().toList());
        List<EnvironmentDetail> details = envs.stream()
                .map(e -> new EnvironmentDetail(e.getId(), e.getName(), e.getKind().name(),
                        domainsByEnv.getOrDefault(e.getId(), List.of()).stream().map(DomainResponse::of).toList(),
                        deploymentsByEnv.getOrDefault(e.getId(), List.of()).stream()
                                .map(d -> DeploymentResponse.of(d, servers.get(d.getServerId())))
                                .sorted(Comparator.comparing(DeploymentResponse::serverName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                                .toList(),
                        e.getCreatedAt(), e.getUpdatedAt(), e.getVersion()))
                .toList();
        return ProjectDetailResponse.of(project, details);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(String q, Integer page, Integer size, String sort) {
        String query = TextRules.clean(q);
        if (query != null && query.length() > QUERY_MAX) {
            throw new ApiException(ProblemType.VALIDATION_ERROR, "Un ou plusieurs filtres sont invalides.",
                    List.of(new FieldViolation("q", QUERY_MAX + " caractères maximum")));
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORT);
        Pageable stable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
        Specification<Project> filter = (root, criteria, cb) -> query == null
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("name")), "%" + escapeLike(Fingerprints.normalizeIdentifier(query)) + "%", '\\');
        Page<Project> found = projects.findAll(filter, stable);
        Map<UUID, Long> counts = found.isEmpty()
                ? Map.of()
                : environments.countByProjectIds(found.map(Project::getId).toList()).stream()
                        .collect(Collectors.toMap(EnvironmentRepository.ProjectCount::getProjectId, EnvironmentRepository.ProjectCount::getCount));
        return PageResponse.from(found.map(p -> ProjectResponse.of(p, counts.getOrDefault(p.getId(), 0L))));
    }

    @Transactional
    public ProjectDetailResponse replace(UUID id, ProjectRequest request) {
        Project project = find(id);
        if (!project.getVersion().equals(request.version())) {
            throw new ApiException(ProblemType.VERSION_CONFLICT, "Cet élément a été modifié par quelqu'un d'autre.");
        }
        Map<String, Object> before = project.auditState();
        project.replace(request.name(), request.description(), request.repositoryUrl(), clock.instant());
        projects.flush();
        Map<String, Map<String, Object>> changes = AuditDiff.changes(before, project.auditState());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("PROJECT_UPDATED", TARGET, id, Map.of("changes", changes)));
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        Project project = projects.findForUpdate(id).orElseThrow(ProjectService::notFound);
        for (Environment environment : environments.findByProjectIdForUpdate(id)) {
            environmentService.softDeleteWithChildren(environment);
        }
        Map<String, Object> snapshot = project.auditState();
        project.delete(clock.instant());
        projects.flush();
        events.publishEvent(AuditEvent.of("PROJECT_DELETED", TARGET, id, snapshot));
    }

    private Project find(UUID id) {
        return projects.findById(id).orElseThrow(ProjectService::notFound);
    }

    static ApiException notFound() {
        return new ApiException(ProblemType.NOT_FOUND, "Projet introuvable.");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
