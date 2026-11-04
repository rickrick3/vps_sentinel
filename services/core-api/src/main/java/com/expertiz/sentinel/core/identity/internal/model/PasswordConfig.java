package com.expertiz.sentinel.core.identity.internal.model;

import java.security.SecureRandom;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class PasswordConfig {

    // Minimum recommandé par l'OWASP pour Argon2id : 19 Mio de mémoire, 2 itérations, parallélisme 1. Les paramètres
    // sont inscrits dans chaque hash : les relever plus tard reste compatible avec les hash existants.
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19 * 1024;
    private static final int ITERATIONS = 2;

    /**
     * Argon2id (spec §8.6), préfixe {@code {argon2}}. Un hash d'un autre format ne correspond jamais : il ne peut pas
     * venir de l'API (contrainte {@code ck_users_password_hash}), et un {@code {noop}} ne doit pas ouvrir de session.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        var argon2 = new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
        var encoder = new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
        encoder.setDefaultPasswordEncoderForMatches(new NeverMatches());
        return encoder;
    }

    @Bean
    TemporaryPasswordGenerator temporaryPasswordGenerator() {
        return new TemporaryPasswordGenerator(new SecureRandom());
    }

    private static final class NeverMatches implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            throw new UnsupportedOperationException("Encodeur de vérification uniquement");
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return false;
        }
    }
}
