package com.expertiz.sentinel.core.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class TextRulesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    static class Holder {
        @NoControlChars
        String singleLine;

        @NoControlChars(allowNewlines = true)
        String description;
    }

    @Test
    void cleanStripsAndTurnsBlankIntoNull() {
        assertThat(TextRules.clean("  a  ")).isEqualTo("a");
        assertThat(TextRules.clean("   ")).isNull();
        assertThat(TextRules.clean(null)).isNull();
    }

    @Test
    void controlCharactersRejected() {
        assertThat(VALIDATOR.validateValue(Holder.class, "singleLine", "bell\u0007")).isNotEmpty();
        assertThat(VALIDATOR.validateValue(Holder.class, "singleLine", "a\nb")).isNotEmpty();
        assertThat(VALIDATOR.validateValue(Holder.class, "singleLine", "plain text")).isEmpty();
    }

    @Test
    void newlinesAndTabsAllowedInDescriptionsOnly() {
        assertThat(VALIDATOR.validateValue(Holder.class, "description", "a\nb\tc")).isEmpty();
        assertThat(VALIDATOR.validateValue(Holder.class, "description", "a\r\nb")).isNotEmpty();
        assertThat(VALIDATOR.validateValue(Holder.class, "description", "nul\u0000")).isNotEmpty();
    }
}
