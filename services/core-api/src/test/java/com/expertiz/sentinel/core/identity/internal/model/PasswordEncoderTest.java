package com.expertiz.sentinel.core.identity.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Hachage Argon2id (spec §8.6) ; un hash d'un autre format ne correspond jamais, sans lever d'exception. */
class PasswordEncoderTest {

    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void hashesWithArgon2idAtTheOwaspMinimum() {
        assertThat(encoder.encode("Fixture-Password-1!")).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
    }

    @Test
    void matchesOnlyTheRightPassword() {
        String hash = encoder.encode("Fixture-Password-1!");

        assertThat(encoder.matches("Fixture-Password-1!", hash)).isTrue();
        assertThat(encoder.matches("fixture-Password-1!", hash)).isFalse();
        assertThat(encoder.matches("Fixture-Password-1", hash)).isFalse();
    }

    @Test
    void saltIsRandom() {
        String first = encoder.encode("Fixture-Password-1!");
        String second = encoder.encode("Fixture-Password-1!");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("Fixture-Password-1!", second)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{noop}Fixture-Password-1!",
        "Fixture-Password-1!",
        "{bcrypt}$2a$10$0123456789012345678901uLb5jT3B1Xb0E6h6qkB8Yqf3S8cG7m2",
        "{sha256}0123456789abcdef",
        "{argon2}",
        "{argon2}garbage",
        ""
    })
    void foreignOrMalformedHashesNeverMatch(String stored) {
        assertThat(encoder.matches("Fixture-Password-1!", stored)).isFalse();
    }
}
