package com.expertiz.sentinel.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.RoleRepository;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.support.Fixtures;
import com.expertiz.sentinel.core.support.IntegrationTest;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Utilisateurs sur PostgreSQL réel : identifiants, unicité de l'email parmi les actifs, rôle unique (spec §9.1, §9.2). */
class UserPersistenceIT extends IntegrationTest {

    // Forme acceptée par la contrainte ck_users_password_hash ; le contenu importe peu ici.
    static final String HASH = "{argon2}$argon2id$v=19$m=19456,t=2,p=1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    private User newUser(String email, RoleCode role) {
        return new User(email, "Alice Martin", HASH, roles.findByCode(role).orElseThrow(), false, null, clock.instant());
    }

    private void softDelete(UUID id) {
        appJdbc().sql("UPDATE users SET deleted_at = now(), status = 'DISABLED' WHERE id = :id").param("id", id).update();
    }

    @Test
    void idsAreUuidV7AssignedBeforeInsert() {
        User user = newUser(Fixtures.uniqueEmail(), RoleCode.VIEWER);
        UUID assigned = user.getId();

        users.save(user);

        assertThat(assigned.version()).isEqualTo(7);
        assertThat(ownerJdbc().sql("SELECT id FROM users WHERE email = :e").param("e", user.getEmail()).query(UUID.class).single())
                .isEqualTo(assigned);
    }

    @Test
    void newUserDefaults() {
        User saved = users.save(newUser(Fixtures.uniqueEmail(), RoleCode.DEVOPS));

        User loaded = users.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getRole().getCode()).isEqualTo(RoleCode.DEVOPS);
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getCreatedAt()).isEqualTo(clock.instant()).isEqualTo(loaded.getUpdatedAt());
        assertThat(loaded.getLastLoginAt()).isNull();
        assertThat(loaded.getDeletedAt()).isNull();
    }

    @Test
    void emailNormalizedOnCreation() {
        String email = Fixtures.uniqueEmail();

        User user = users.save(newUser("  " + email.toUpperCase(Locale.ROOT) + " ", RoleCode.VIEWER));

        assertThat(user.getEmail()).isEqualTo(email);
    }

    @Test
    void emailUniqueAmongActiveOnly() {
        String email = Fixtures.uniqueEmail();
        User first = users.save(newUser(email, RoleCode.VIEWER));

        assertThatThrownBy(() -> users.save(newUser(email.toUpperCase(Locale.ROOT), RoleCode.VIEWER)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uq_users_email_active");

        softDelete(first.getId());
        User again = users.save(newUser(email, RoleCode.VIEWER));
        assertThat(again.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void deletedUserIsInvisible() {
        User user = users.save(newUser(Fixtures.uniqueEmail(), RoleCode.VIEWER));

        softDelete(user.getId());

        assertThat(users.findById(user.getId())).isEmpty();
    }

    @Test
    void oneRolePerUser() {
        User user = users.save(newUser(Fixtures.uniqueEmail(), RoleCode.VIEWER));

        assertThat(appJdbc().sql("SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = :id")
                        .param("id", user.getId())
                        .query(String.class)
                        .list())
                .containsExactly("VIEWER");
        assertThatThrownBy(() -> appJdbc()
                        .sql("INSERT INTO user_roles (user_id, role_id) SELECT :id, id FROM roles WHERE code = 'SUPER_ADMIN'")
                        .param("id", user.getId())
                        .update())
                .rootCause()
                .hasMessageContaining("uq_user_roles_user");
    }

    @Test
    void auditStateIsWhitelisted() {
        String email = Fixtures.uniqueEmail();
        User user = newUser(email, RoleCode.DEVELOPER);

        assertThat(user.auditState())
                .containsOnlyKeys("email", "fullName", "role", "status", "mustChangePassword")
                .containsEntry("email", email)
                .containsEntry("fullName", "Alice Martin")
                .containsEntry("role", "DEVELOPER")
                .containsEntry("status", "ACTIVE")
                .containsEntry("mustChangePassword", false);
        assertThat(user.auditState().toString()).doesNotContain(HASH);
    }
}
