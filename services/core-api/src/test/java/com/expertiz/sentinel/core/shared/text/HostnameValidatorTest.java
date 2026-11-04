package com.expertiz.sentinel.core.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HostnameValidatorTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    static class Holder {
        @Hostname
        String plain;

        @Hostname(requireDot = true)
        String domain;
    }

    private static boolean plainValid(String value) {
        return VALIDATOR.validateValue(Holder.class, "plain", value).isEmpty();
    }

    private static boolean domainValid(String value) {
        return VALIDATOR.validateValue(Holder.class, "domain", value).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"vps-01", "VPS-01.example.com", "a.b", " vps-01 "})
    void validHostnames(String value) {
        assertThat(plainValid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo.example.com", "xn--bcher-kva.example"})
    void validDomains(String value) {
        assertThat(domainValid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-bad.com", "bad-.com", "bad..com", "demo.example.com.", "*.example.com", "bücher.example", "a b.com"})
    void invalidEverywhere(String value) {
        assertThat(plainValid(value)).isFalse();
        assertThat(domainValid(value)).isFalse();
    }

    @Test
    void labelOf64CharactersIsInvalid() {
        assertThat(plainValid("a".repeat(64) + ".com")).isFalse();
        assertThat(plainValid("a".repeat(63) + ".com")).isTrue();
    }

    @Test
    void nameLongerThan253CharactersIsInvalid() {
        String threeLabels = ("a".repeat(63) + ".").repeat(3);
        assertThat(plainValid(threeLabels + "a".repeat(61))).as("253 caractères").isTrue();
        assertThat(plainValid(threeLabels + "a".repeat(62))).as("254 caractères").isFalse();
    }

    @Test
    void dotRequiredOnlyForDomains() {
        assertThat(plainValid("example")).isTrue();
        assertThat(domainValid("example")).isFalse();
    }

    @Test
    void absentOrBlankIsLeftToNotBlank() {
        assertThat(plainValid(null)).isTrue();
        assertThat(plainValid("   ")).isTrue();
    }
}
