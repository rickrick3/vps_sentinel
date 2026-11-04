package com.expertiz.sentinel.core.infrastructure.internal;

import com.expertiz.sentinel.core.infrastructure.AdminStatus;
import com.expertiz.sentinel.core.infrastructure.ServerUsageChecker;
import com.expertiz.sentinel.core.shared.audit.AuditDiff;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import com.expertiz.sentinel.core.shared.text.IpAddresses;
import com.expertiz.sentinel.core.shared.text.TextRules;
import com.expertiz.sentinel.core.shared.web.PageRequests;
import com.expertiz.sentinel.core.shared.web.PageResponse;
import com.expertiz.sentinel.core.shared.web.SortSpec;
import jakarta.persistence.criteria.Predicate;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventaire des VPS (spec §9.3, §10.1). Supprimer un VPS ou le passer en {@code DECOMMISSIONED} suit l'ordre « verrou,
 * puis vérification, puis décision » (spec §7.2) : {@code SELECT … FOR UPDATE}, puis consultation des
 * {@link ServerUsageChecker} ; un déploiement concurrent attend ce verrou et relit ensuite l'état du VPS.
 */
@Service
class ServerService {

    static final SortSpec SORT = new SortSpec(
            Map.of("name", "name", "adminStatus", "adminStatus", "provider", "provider", "region", "region", "createdAt", "createdAt"),
            "name,asc");

    private static final String TARGET = "SERVER";
    private static final int QUERY_MAX = 254;

    private final ServerRepository servers;
    private final ObjectProvider<ServerUsageChecker> usageCheckers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ServerService(ServerRepository servers, ObjectProvider<ServerUsageChecker> usageCheckers, ApplicationEventPublisher events,
            Clock clock) {
        this.servers = servers;
        this.usageCheckers = usageCheckers;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ServerResponse create(ServerRequest request) {
        Server server = servers.saveAndFlush(new Server(data(request), clock.instant()));
        events.publishEvent(AuditEvent.of("SERVER_CREATED", TARGET, server.getId(), server.auditState()));
        return ServerResponse.of(server);
    }

    @Transactional(readOnly = true)
    public ServerResponse get(UUID id) {
        return ServerResponse.of(find(id));
    }

    /** Remplacement complet (spec §10.2) : un champ facultatif absent est vidé, les tags sont remplacés en bloc. */
    @Transactional
    public ServerResponse replace(UUID id, ServerRequest request) {
        Server.Data data = data(request);
        boolean decommissioning = data.adminStatus() == AdminStatus.DECOMMISSIONED;
        Server server = decommissioning ? findForUpdate(id) : find(id);
        if (!server.getVersion().equals(request.version())) {
            throw new ApiException(ProblemType.VERSION_CONFLICT, "Cet élément a été modifié par quelqu'un d'autre.");
        }
        if (decommissioning) {
            ensureUnused(id);
        }
        Map<String, Object> before = server.auditState();
        server.replace(data, clock.instant());
        servers.flush();
        Map<String, Map<String, Object>> changes = AuditDiff.changes(before, server.auditState());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("SERVER_UPDATED", TARGET, id, Map.of("changes", changes)));
        }
        return ServerResponse.of(server);
    }

    /** Suppression logique (spec §9.6) : refusée tant qu'un déploiement utilise le VPS ; ses tags sont conservés. */
    @Transactional
    public void delete(UUID id) {
        Server server = findForUpdate(id);
        ensureUnused(id);
        Map<String, Object> snapshot = server.auditState();
        server.delete(clock.instant());
        servers.flush();
        events.publishEvent(AuditEvent.of("SERVER_DELETED", TARGET, id, snapshot));
    }

    /** {@code q} cherche dans le nom et le hostname ; si c'est une adresse IP, aussi dans les deux adresses. */
    @Transactional(readOnly = true)
    public PageResponse<ServerResponse> list(String q, String tag, String provider, String region, String adminStatus,
            Integer page, Integer size, String sort) {
        List<FieldViolation> violations = new ArrayList<>();
        String query = TextRules.clean(q);
        if (query != null && query.length() > QUERY_MAX) {
            violations.add(new FieldViolation("q", QUERY_MAX + " caractères maximum"));
        }
        String tagFilter = TextRules.clean(tag);
        if (tagFilter != null && TagRules.normalizeOne(tagFilter) == null) {
            violations.add(new FieldViolation("tag", "tag invalide"));
        }
        AdminStatus statusFilter = null;
        String status = TextRules.clean(adminStatus);
        if (status != null) {
            statusFilter = Arrays.stream(AdminStatus.values()).filter(s -> s.name().equals(status)).findFirst().orElse(null);
            if (statusFilter == null) {
                violations.add(new FieldViolation("adminStatus", "statut inconnu"));
            }
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ProblemType.VALIDATION_ERROR, "Un ou plusieurs filtres sont invalides.", violations);
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORT);
        Pageable stable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
        Specification<Server> filter = filter(query, tagFilter == null ? null : TagRules.normalizeOne(tagFilter),
                TextRules.clean(provider), TextRules.clean(region), statusFilter);
        return PageResponse.from(servers.findAll(filter, stable).map(ServerResponse::of));
    }

    @Transactional(readOnly = true)
    public ServerFacetsResponse facets() {
        return new ServerFacetsResponse(servers.providers(), servers.regions(), servers.tags());
    }

    private Server find(UUID id) {
        return servers.findById(id).orElseThrow(ServerService::notFound);
    }

    private Server findForUpdate(UUID id) {
        return servers.findForUpdate(id).orElseThrow(ServerService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(ProblemType.NOT_FOUND, "VPS introuvable.");
    }

    /** Sous le verrou du VPS : aucun déploiement ne peut apparaître avant la fin de la transaction. */
    private void ensureUnused(UUID id) {
        if (usageCheckers.orderedStream().anyMatch(checker -> checker.hasDeployments(id))) {
            throw new ApiException(ProblemType.SERVER_IN_USE, "VPS utilisé par des déploiements");
        }
    }

    private static Server.Data data(ServerRequest request) {
        return new Server.Data(
                request.name(),
                request.hostname() == null ? null : request.hostname().toLowerCase(Locale.ROOT),
                ip(request.publicIp()),
                ip(request.privateIp()),
                request.provider(),
                request.region(),
                request.osFamily() == null ? null : OsFamily.valueOf(request.osFamily()),
                request.osVersion(),
                request.cpuCores() == null ? null : request.cpuCores().shortValue(),
                request.ramMb(),
                request.diskGb(),
                request.adminStatus() == null ? AdminStatus.ACTIVE : AdminStatus.valueOf(request.adminStatus()),
                request.description(),
                TagRules.normalize(request.tags()));
    }

    private static InetAddress ip(String literal) {
        return literal == null ? null : IpAddresses.parse(literal).orElseThrow();
    }

    private static Specification<Server> filter(String query, String tag, String provider, String region, AdminStatus status) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                String pattern = "%" + escapeLike(Fingerprints.normalizeIdentifier(query)) + "%";
                List<Predicate> matches = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(root.get("hostname"), pattern, '\\')));
                Optional<InetAddress> ip = IpAddresses.parse(query);
                ip.ifPresent(address -> {
                    matches.add(cb.equal(root.get("publicIp"), address));
                    matches.add(cb.equal(root.get("privateIp"), address));
                });
                predicates.add(cb.or(matches.toArray(Predicate[]::new)));
            }
            if (tag != null) {
                predicates.add(cb.isMember(tag, root.get("tags")));
            }
            if (provider != null) {
                predicates.add(cb.equal(cb.lower(root.get("provider")), provider.toLowerCase(Locale.ROOT)));
            }
            if (region != null) {
                predicates.add(cb.equal(cb.lower(root.get("region")), region.toLowerCase(Locale.ROOT)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("adminStatus"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
