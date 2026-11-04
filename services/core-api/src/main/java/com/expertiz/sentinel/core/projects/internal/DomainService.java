package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domaines (spec §9.4, §10.1). L'environnement est verrouillé avant toute écriture : en partage pour un domaine
 * secondaire, en exclusif pour un domaine principal, afin que deux bascules simultanées laissent exactement un
 * principal.
 */
@Service
class DomainService {

    static final String TARGET = "DOMAIN";

    private final EnvironmentRepository environments;
    private final DomainRepository domains;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DomainService(EnvironmentRepository environments, DomainRepository domains, ApplicationEventPublisher events, Clock clock) {
        this.environments = environments;
        this.domains = domains;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public DomainResponse add(UUID environmentId, DomainRequest request) {
        boolean primary = Boolean.TRUE.equals(request.isPrimary());
        (primary ? environments.findLiveForUpdate(environmentId) : environments.findLiveForShare(environmentId))
                .orElseThrow(EnvironmentService::notFound);
        if (primary) {
            domains.clearPrimary(environmentId);
        }
        Domain domain = domains.saveAndFlush(
                new Domain(environmentId, request.hostname().toLowerCase(Locale.ROOT), primary, clock.instant()));
        events.publishEvent(AuditEvent.of("DOMAIN_ADDED", TARGET, domain.getId(), domain.auditState()));
        return DomainResponse.of(domain);
    }

    @Transactional
    public void remove(UUID id) {
        Domain domain = domains.findById(id).orElseThrow(DomainService::notFound);
        environments.findLiveForShare(domain.getEnvironmentId()).orElseThrow(DomainService::notFound);
        domains.delete(domain);
        domains.flush();
        events.publishEvent(AuditEvent.of("DOMAIN_REMOVED", TARGET, id, domain.auditState()));
    }

    static ApiException notFound() {
        return new ApiException(ProblemType.NOT_FOUND, "Domaine introuvable.");
    }
}
