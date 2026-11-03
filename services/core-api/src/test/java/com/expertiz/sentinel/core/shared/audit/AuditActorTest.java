package com.expertiz.sentinel.core.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditActorTest {

    @Test
    void factories() {
        UUID id = UUID.randomUUID();

        assertThat(AuditActor.user(id, "alice@example.com")).isEqualTo(new AuditActor(ActorType.USER, id, "alice@example.com"));
        assertThat(AuditActor.anonymous()).isEqualTo(new AuditActor(ActorType.ANONYMOUS, null, null));
        assertThat(AuditActor.system()).isEqualTo(new AuditActor(ActorType.SYSTEM, null, null));
    }

    // actor_email est la copie figée de l'email d'un utilisateur authentifié, jamais une saisie (spec §9.5).
    @Test
    void onlyUsersCarryAnIdentity() {
        assertThatThrownBy(() -> new AuditActor(ActorType.ANONYMOUS, null, "typed@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditActor(ActorType.SYSTEM, UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditActor(ActorType.USER, null, "a@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
