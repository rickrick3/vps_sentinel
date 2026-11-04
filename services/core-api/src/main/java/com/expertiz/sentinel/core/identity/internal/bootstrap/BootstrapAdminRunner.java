package com.expertiz.sentinel.core.identity.internal.bootstrap;

import com.expertiz.sentinel.core.identity.internal.admin.SuperAdminLock;
import com.expertiz.sentinel.core.identity.internal.model.PasswordPolicy;
import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.RoleRepository;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.shared.audit.AuditActor;
import com.expertiz.sentinel.core.shared.audit.AuditEvent;
import com.expertiz.sentinel.core.shared.crypto.Fingerprints;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.text.EmailAddress;
import com.expertiz.sentinel.core.shared.text.TextRules;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Premier SUPER_ADMIN (spec §8.7), évalué à chaque démarrage sous le verrou des SUPER_ADMIN. S'il en existe un actif,
 * les variables sont ignorées sans erreur. Sinon, une variable absente, un email invalide, un mot de passe hors
 * politique ou un email déjà pris sont journalisés et rien n'est créé : jamais de promotion ni de réactivation. Aucun
 * de ces cas ne bloque le démarrage, et le mot de passe n'est jamais journalisé.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private static final String DEFAULT_NAME = "Administrateur";
    private static final int MAX_NAME_LENGTH = 120;

    private final String email;
    private final String password;
    private final String name;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final SuperAdminLock lock;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    BootstrapAdminRunner(
            @Value("${sentinel.bootstrap.admin.email:}") String email,
            @Value("${sentinel.bootstrap.admin.password:}") String password,
            @Value("${sentinel.bootstrap.admin.name:}") String name,
            UserRepository users, RoleRepository roles, PasswordEncoder encoder, SuperAdminLock lock,
            ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.lock = lock;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap(email, password, name);
    }

    public void bootstrap(String rawEmail, String rawPassword, String rawName) {
        String cleanEmail = TextRules.clean(rawEmail);
        if (cleanEmail == null || rawPassword == null || rawPassword.isBlank()) {
            log.debug("Variables de bootstrap absentes : aucun administrateur à créer.");
            return;
        }
        transaction.executeWithoutResult(status ->
                createIfNoActiveSuperAdmin(Fingerprints.normalizeIdentifier(cleanEmail), rawPassword, TextRules.clean(rawName)));
    }

    private void createIfNoActiveSuperAdmin(String normalizedEmail, String rawPassword, String cleanName) {
        lock.acquire();
        if (users.countByRoleCodeAndStatus(RoleCode.SUPER_ADMIN, UserStatus.ACTIVE) > 0) {
            log.info("Un SUPER_ADMIN actif existe : variables de bootstrap ignorées. Elles peuvent être retirées.");
            return;
        }
        if (!EmailAddress.isValid(normalizedEmail)) {
            log.error("SENTINEL_BOOTSTRAP_ADMIN_EMAIL n'est pas un email valide : aucun administrateur créé.");
            return;
        }
        try {
            PasswordPolicy.check("password", rawPassword, normalizedEmail);
        } catch (ApiException violation) {
            log.error("SENTINEL_BOOTSTRAP_ADMIN_PASSWORD ne respecte pas la politique des mots de passe ({}) : "
                    + "aucun administrateur créé.", violation.errors().getFirst().message());
            return;
        }
        String fullName = cleanName == null ? DEFAULT_NAME : cleanName;
        if (fullName.codePointCount(0, fullName.length()) > MAX_NAME_LENGTH) {
            log.error("SENTINEL_BOOTSTRAP_ADMIN_NAME dépasse {} caractères : aucun administrateur créé.", MAX_NAME_LENGTH);
            return;
        }
        if (users.findByEmail(normalizedEmail).isPresent()) {
            log.error("SENTINEL_BOOTSTRAP_ADMIN_EMAIL appartient déjà à un compte, qui n'est ni promu ni réactivé : "
                    + "aucun administrateur créé.");
            return;
        }
        User admin = users.save(new User(normalizedEmail, fullName, encoder.encode(rawPassword),
                roles.findByCode(RoleCode.SUPER_ADMIN).orElseThrow(), true, null, clock.instant()));
        events.publishEvent(AuditEvent.of("USER_CREATED", "USER", admin.getId(), admin.auditState()).withActor(AuditActor.system()));
        log.info("Premier SUPER_ADMIN créé ; changement de mot de passe imposé à la première connexion. "
                + "Retirez les variables SENTINEL_BOOTSTRAP_ADMIN_*.");
    }
}
