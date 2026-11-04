package com.expertiz.sentinel.core.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** {@code @EmailFormat} juge la forme normalisée, celle qui sera stockée ; l'absence est laissée à {@code @NotBlank}. */
class EmailFormatValidatorTest {

    private final EmailFormatValidator validator = new EmailFormatValidator();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"admin@sentinel.local", "  Admin@Sentinel.LOCAL ", "ａｄｍｉｎ@sentinel.local"})
    void accepted(String email) {
        assertThat(validator.isValid(email, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "admin@localhost", "ad min@sentinel.local", "admin@sentinel.local\u0007"})
    void rejected(String email) {
        assertThat(validator.isValid(email, null)).isFalse();
    }
}
