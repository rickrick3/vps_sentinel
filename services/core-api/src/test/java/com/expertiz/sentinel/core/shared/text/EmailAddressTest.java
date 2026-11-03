package com.expertiz.sentinel.core.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Forme d'un email normalisé (spec §10.3) : {@code local@domaine}, domaine avec au moins un point, 254 caractères max. */
class EmailAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {"admin@sentinel.local", "a.b+c@example.co.uk", "x@y.z", "élise@exemple.fr"})
    void valid(String email) {
        assertThat(EmailAddress.isValid(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "admin",
        "admin@localhost",
        "@sentinel.local",
        "admin@",
        "admin@.local",
        "admin@sentinel.",
        "admin@@sentinel.local",
        "ad min@sentinel.local",
        "admin@sentinel.local\n",
        "admin\u0007@sentinel.local"
    })
    void invalid(String email) {
        assertThat(EmailAddress.isValid(email)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {254, 255})
    void atMost254Characters(int length) {
        String email = "a".repeat(length - "@sentinel.local".length()) + "@sentinel.local";

        assertThat(EmailAddress.isValid(email)).isEqualTo(length <= 254);
    }

    @Test
    void nullIsInvalid() {
        assertThat(EmailAddress.isValid(null)).isFalse();
    }
}
