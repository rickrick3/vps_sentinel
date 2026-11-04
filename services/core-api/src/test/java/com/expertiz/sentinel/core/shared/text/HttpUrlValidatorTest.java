package com.expertiz.sentinel.core.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpUrlValidatorTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    static class Holder {
        @HttpUrl
        String url;
    }

    private static boolean valid(String value) {
        return VALIDATOR.validateValue(Holder.class, "url", value).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/org/repo", "http://gitlab.internal:8080/team/app", "HTTPS://Example.com/x"})
    void absoluteHttpUrls(String value) {
        assertThat(valid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "ftp://x/y",
        "https://user:pass@host/x",
        "https://token@host/x",
        "//host/x",
        "github.com/x",
        "https:///nohost",
        "https://exa mple.com"
    })
    void rejected(String value) {
        assertThat(valid(value)).isFalse();
    }
}
