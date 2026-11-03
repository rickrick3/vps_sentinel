package com.expertiz.sentinel.core.identity.internal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Politique du spec §8.6 : 12 à 128 points de code, différent de l'email, aucune règle de composition. */
class PasswordPolicyTest {

    static final String EMAIL = "alice@example.com";

    private static FieldViolation violation(String field, String password) {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> PasswordPolicy.check(field, password, EMAIL));
        assertThat(ex).as("violation attendue").isNotNull();
        assertThat(ex.type()).isEqualTo(ProblemType.VALIDATION_ERROR);
        assertThat(ex.errors()).hasSize(1);
        return ex.errors().getFirst();
    }

    private static void accepted(String password) {
        assertThatCode(() -> PasswordPolicy.check("password", password, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void rejects11Accepts12() {
        assertThat(violation("password", "a".repeat(11))).isEqualTo(new FieldViolation("password", "12 caractères minimum."));
        accepted("a".repeat(12));
    }

    @Test
    void accepts128Rejects129() {
        accepted("a".repeat(128));
        assertThat(violation("password", "a".repeat(129))).isEqualTo(new FieldViolation("password", "128 caractères maximum."));
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        // Chaque émoji compte pour un caractère, mais pour deux unités UTF-16.
        accepted("😀".repeat(12));
        assertThat(violation("password", "😀".repeat(11)).message()).isEqualTo("12 caractères minimum.");
        accepted("😀".repeat(128));
        assertThat(violation("password", "😀".repeat(129)).message()).isEqualTo("128 caractères maximum.");
    }

    @Test
    void nullOrEmptyIsTooShort() {
        assertThat(violation("password", null).message()).isEqualTo("12 caractères minimum.");
        assertThat(violation("password", "").message()).isEqualTo("12 caractères minimum.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice@example.com", "Alice@Example.COM", "  alice@example.com ", "ａｌｉｃｅ@example.com"})
    void rejectsTheEmailIgnoringCaseSpacesAndWidth(String password) {
        assertThat(violation("password", password))
                .isEqualTo(new FieldViolation("password", "Le mot de passe doit être différent de l'email."));
    }

    @Test
    void violationTargetsTheGivenField() {
        assertThat(violation("newPassword", "short").field()).isEqualTo("newPassword");
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaaaaaaaaaaa", "correct horse battery staple", "alice@example.co", "123456789012"})
    void noCompositionRules(String password) {
        accepted(password);
    }

    @Test
    void messagesNeverEchoThePassword() {
        String password = "S3cr3t-P";
        ApiException ex = catchThrowableOfType(ApiException.class, () -> PasswordPolicy.check("password", password, EMAIL));

        assertThat(ex.getMessage()).doesNotContain(password);
        assertThat(ex.errors().toString()).doesNotContain(password);
    }

    @Test
    void withoutEmailOnlyLengthApplies() {
        assertThatCode(() -> PasswordPolicy.check("password", "a".repeat(12), null)).doesNotThrowAnyException();
    }
}
