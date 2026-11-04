package com.expertiz.sentinel.core.support;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.RoleRepository;
import com.expertiz.sentinel.core.identity.internal.model.User;
import com.expertiz.sentinel.core.identity.internal.model.UserRepository;
import com.expertiz.sentinel.core.identity.internal.security.AccessTokenIssuer;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Données de test uniques (conventions de test du plan) : la base est partagée et {@code audit_logs} ne peut pas être
 * vidée, chaque test crée donc ses propres utilisateurs, emails, IP et empreintes.
 */
@TestComponent
public class Fixtures {

    public static final String PASSWORD = "Fixture-Password-1!";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    // Argon2 coûte quelques dizaines de millisecondes : le hash du mot de passe commun est calculé une fois.
    private static volatile String passwordHash;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AccessTokenIssuer issuer;
    private final JdbcClient jdbc;
    private final Clock clock;

    Fixtures(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AccessTokenIssuer issuer, JdbcClient jdbc,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.issuer = issuer;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record TestUser(UUID id, String email, String password, RoleCode role) {}

    /** Utilisateur actif, sans changement de mot de passe imposé, avec le mot de passe {@link #PASSWORD}. */
    public TestUser createUser(RoleCode role) {
        User user = users.save(new User(uniqueEmail(), "Utilisateur de test", passwordHash(),
                roles.findByCode(role).orElseThrow(), false, null, clock.instant()));
        return new TestUser(user.getId(), user.getEmail(), PASSWORD, role);
    }

    /** Jeton d'accès émis par l'application, claims calculés depuis la base. */
    public String accessToken(TestUser user) {
        return issuer.issue(users.findById(user.id()).orElseThrow()).value();
    }

    /** Jeton {@code pcr = true} : le changement de mot de passe est d'abord imposé en base, comme pour un vrai compte. */
    public String accessTokenWithPcr(TestUser user) {
        jdbc.sql("UPDATE users SET must_change_password = true WHERE id = :id").param("id", user.id()).update();
        return accessToken(user);
    }

    public static String uniqueEmail() {
        return "user-" + SEQUENCE.incrementAndGet() + "-" + Long.toHexString(System.nanoTime()) + "@sentinel.test";
    }

    /** Nom unique par appel ({@code préfixe-n-hex}), conforme aux motifs de noms du spec §10.3. */
    public static String uniqueName(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-" + Long.toHexString(System.nanoTime());
    }

    /** Adresse {@code 10.x.y.z} unique par appel : les compteurs de limitation ne se mélangent pas entre tests. */
    public static String uniqueIp() {
        int n = SEQUENCE.incrementAndGet();
        return "10." + ((n >> 16) & 0xff) + "." + ((n >> 8) & 0xff) + "." + (n & 0xff);
    }

    /** Adresse IPv6 de documentation ({@code 2001:db8::/32}) unique par appel, sous forme compacte. */
    public static String uniqueIpv6() {
        return "2001:db8::" + Integer.toHexString(SEQUENCE.incrementAndGet());
    }

    /** Valeur au format d'une empreinte HMAC tronquée (32 caractères hexadécimaux), unique par appel. */
    public static String uniqueFingerprint() {
        return HexFormat.of().toHexDigits(System.nanoTime()) + HexFormat.of().toHexDigits((long) SEQUENCE.incrementAndGet());
    }

    private String passwordHash() {
        if (passwordHash == null) {
            passwordHash = encoder.encode(PASSWORD);
        }
        return passwordHash;
    }
}
