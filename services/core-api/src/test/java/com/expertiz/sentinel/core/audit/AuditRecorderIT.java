package com.expertiz.sentinel.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.shared.audit.AuditActor;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.web.CorrelationIdFilter;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Écriture synchrone de l'audit, dans la transaction de l'appelant (spec §7.3). */
class AuditRecorderIT extends IntegrationTest {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate tx;
    UUID target;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        target = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private void publishInTransaction(AuditEvent event) {
        tx.executeWithoutResult(status -> events.publishEvent(event));
    }

    private Map<String, Object> row() {
        return ownerJdbc()
                .sql("""
                        SELECT actor_type, actor_user_id, actor_email, action, target_type, target_id,
                               details ->> 'name' AS detail_name, host(ip) AS ip, correlation_id, occurred_at
                          FROM audit_logs WHERE target_id = :target""")
                .param("target", target)
                .query()
                .singleRow();
    }

    private long rows() {
        return ownerJdbc().sql("SELECT count(*) FROM audit_logs WHERE target_id = :target").param("target", target).query(Long.class).single();
    }

    @Test
    void committedTransactionWritesRow() {
        Instant now = Instant.parse("2026-11-03T08:15:30.123456Z");
        clock.set(now);

        publishInTransaction(AuditEvent.of("SERVER_CREATED", "SERVER", target, Map.of("name", "VPS-01")));

        Map<String, Object> row = row();
        assertThat(row)
                .containsEntry("action", "SERVER_CREATED")
                .containsEntry("target_type", "SERVER")
                .containsEntry("target_id", target)
                .containsEntry("detail_name", "VPS-01");
        assertThat(((Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(now);
    }

    @Test
    void rolledBackTransactionWritesNothing() {
        tx.executeWithoutResult(status -> {
            events.publishEvent(AuditEvent.of("SERVER_CREATED", "SERVER", target, Map.of()));
            status.setRollbackOnly();
        });

        assertThat(rows()).isZero();
    }

    @Test
    void publishingOutsideTransactionFails() {
        assertThatThrownBy(() -> events.publishEvent(AuditEvent.of("SERVER_CREATED", "SERVER", target, Map.of())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(rows()).isZero();
    }

    @Test
    void withoutAuthenticationActorIsAnonymous() {
        publishInTransaction(AuditEvent.of("LOGIN_FAILED", "USER", target, Map.of()));

        assertThat(row()).containsEntry("actor_type", "ANONYMOUS").containsEntry("actor_user_id", null).containsEntry("actor_email", null);
    }

    @Test
    void explicitSystemActor() {
        publishInTransaction(AuditEvent.of("USER_CREATED", "USER", target, Map.of()).withActor(AuditActor.system()));

        assertThat(row()).containsEntry("actor_type", "SYSTEM").containsEntry("actor_user_id", null);
    }

    @Test
    void explicitAnonymousActorWinsOverAuthentication() {
        authenticate(UUID.randomUUID(), "bob@example.com");

        publishInTransaction(AuditEvent.of("LOGIN_FAILED", "USER", target, Map.of()).withActor(AuditActor.anonymous()));

        assertThat(row()).containsEntry("actor_type", "ANONYMOUS").containsEntry("actor_email", null);
    }

    @Test
    void jwtAuthenticatedUserIsTheActor() {
        UUID userId = UUID.randomUUID();
        authenticate(userId, "alice@example.com");

        publishInTransaction(AuditEvent.of("SERVER_UPDATED", "SERVER", target, Map.of()));

        assertThat(row())
                .containsEntry("actor_type", "USER")
                .containsEntry("actor_user_id", userId)
                .containsEntry("actor_email", "alice@example.com");
    }

    @Test
    void requestIpAndCorrelationRecorded() {
        bindRequest("10.9.8.7");
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1");

        publishInTransaction(AuditEvent.of("SERVER_CREATED", "SERVER", target, Map.of()));

        assertThat(row()).containsEntry("ip", "10.9.8.7").containsEntry("correlation_id", "corr-1");
    }

    @Test
    void ipv6ClientRecorded() {
        bindRequest("2001:db8::1");

        publishInTransaction(AuditEvent.of("SERVER_CREATED", "SERVER", target, Map.of()));

        assertThat(row()).containsEntry("ip", "2001:db8::1");
    }

    @Test
    void outsideARequestIpAndCorrelationAreNull() {
        publishInTransaction(AuditEvent.of("SESSIONS_PURGED", null, target, Map.of()).withActor(AuditActor.system()));

        assertThat(row()).containsEntry("ip", null).containsEntry("correlation_id", null);
    }

    private static void bindRequest(String remoteAddr) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void authenticate(UUID userId, String email) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("email", email)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
