package com.expertiz.sentinel.core.audit.internal;

import com.expertiz.sentinel.core.shared.audit.AuditActor;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.persistence.UuidV7;
import com.expertiz.sentinel.core.shared.web.CorrelationIdFilter;
import com.expertiz.sentinel.core.shared.web.RequestInfo;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Écrit chaque {@link AuditEvent} de façon synchrone, dans la transaction de l'appelant (spec §7.3) : si elle est
 * annulée, l'audit l'est aussi. Publier hors transaction est une erreur de programmation, d'où {@code MANDATORY}.
 */
@Component
class AuditRecorder {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final AuditActorResolver actors;
    private final JsonMapper json;

    AuditRecorder(JdbcClient jdbc, Clock clock, AuditActorResolver actors, JsonMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.actors = actors;
        this.json = json;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        AuditActor actor = actors.resolve(event.actor());
        jdbc.sql("""
                        INSERT INTO audit_logs (id, occurred_at, actor_type, actor_user_id, actor_email, action,
                                                target_type, target_id, details, ip, correlation_id)
                        VALUES (:id, :occurredAt, :actorType, :actorUserId, :actorEmail, :action,
                                :targetType, :targetId, CAST(:details AS jsonb), CAST(:ip AS inet), :correlationId)""")
                .param("id", UuidV7.next())
                .param("occurredAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("actorType", actor.type().name())
                .param("actorUserId", actor.userId())
                .param("actorEmail", actor.email())
                .param("action", event.action())
                .param("targetType", event.targetType())
                .param("targetId", event.targetId())
                .param("details", json.writeValueAsString(event.details()))
                .param("ip", RequestInfo.clientIp().orElse(null))
                .param("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY))
                .update();
    }
}
