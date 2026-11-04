package com.expertiz.sentinel.core.identity.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.identity.internal.model.UserStatus;
import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code PATCH /users/{id}} (spec §10.2) : un champ absent reste inchangé, {@code null} est refusé, un champ inconnu
 * aussi, et {@code version} est obligatoire. Toutes les erreurs sont renvoyées ensemble, chacune sur son champ.
 */
class PatchUserCommandTest {

    private final JsonMapper json = JsonMapper.builder().build();

    // Même désérialisation que le contrôleur : un objet JSON en Map, valeurs null comprises.
    @SuppressWarnings("unchecked")
    private PatchUserCommand parse(String raw) {
        return PatchUserCommand.parse(json.readValue(raw, Map.class));
    }

    private List<FieldViolation> rejected(String raw) {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> parse(raw));
        assertThat(ex).as("refus attendu pour %s", raw).isNotNull();
        assertThat(ex.type()).isEqualTo(ProblemType.VALIDATION_ERROR);
        return ex.errors();
    }

    private List<String> rejectedFields(String raw) {
        return rejected(raw).stream().map(FieldViolation::field).toList();
    }

    @Test
    void absentFieldsUnchanged() {
        PatchUserCommand command = parse("{\"version\":3}");

        assertThat(command.fullName()).isEmpty();
        assertThat(command.role()).isEmpty();
        assertThat(command.status()).isEmpty();
        assertThat(command.version()).isEqualTo(3);
        assertThat(command.changesRoleOrStatus()).isFalse();
    }

    @Test
    void presentFieldsParsedAndCleaned() {
        PatchUserCommand command = parse("{\"fullName\":\"  Alice Martin \",\"role\":\"DEVOPS\",\"status\":\"DISABLED\",\"version\":0}");

        assertThat(command.fullName()).contains("Alice Martin");
        assertThat(command.role()).contains(RoleCode.DEVOPS);
        assertThat(command.status()).contains(UserStatus.DISABLED);
        assertThat(command.version()).isZero();
        assertThat(command.changesRoleOrStatus()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"fullName", "role", "status"})
    void explicitNullRejected(String field) {
        assertThat(rejected("{\"" + field + "\":null,\"version\":1}"))
                .containsExactly(new FieldViolation(field, "ne doit pas être null"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "passwordHash", "mustChangePassword", "id", "deletedAt", "Role"})
    void unknownOrReadOnlyFieldRejected(String field) {
        assertThat(rejected("{\"" + field + "\":\"x\",\"version\":1}"))
                .containsExactly(new FieldViolation(field, "champ inconnu ou non modifiable"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"version\":null}", "{\"version\":\"1\"}", "{\"version\":-1}", "{\"version\":1.5}"})
    void versionRequiredAsANonNegativeInteger(String raw) {
        assertThat(rejectedFields(raw)).containsExactly("version");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"role":"ROOT","version":1}                | role     | rôle inconnu
            {"role":"devops","version":1}              | role     | rôle inconnu
            {"role":7,"version":1}                     | role     | doit être une chaîne de caractères
            {"status":"LOCKED","version":1}            | status   | statut inconnu
            {"fullName":"","version":1}                | fullName | ne doit pas être vide
            {"fullName":"   ","version":1}             | fullName | ne doit pas être vide
            {"fullName":"Bob\\u0007","version":1}      | fullName | ne doit pas contenir de caractères de contrôle
            {"fullName":["Bob"],"version":1}           | fullName | doit être une chaîne de caractères
            """)
    void invalidValuesRejectedOnTheirField(String raw, String field, String message) {
        assertThat(rejected(raw)).containsExactly(new FieldViolation(field, message));
    }

    @Test
    void fullNameAtMost120Characters() {
        assertThat(parse("{\"fullName\":\"" + "é".repeat(120) + "\",\"version\":1}").fullName()).isPresent();
        assertThat(rejected("{\"fullName\":\"" + "é".repeat(121) + "\",\"version\":1}"))
                .containsExactly(new FieldViolation("fullName", "120 caractères maximum"));
    }

    @Test
    void allViolationsReportedTogether() {
        assertThat(rejectedFields("{\"fullName\":null,\"role\":\"ROOT\",\"email\":\"a@b.c\"}"))
                .containsExactlyInAnyOrder("fullName", "role", "email", "version");
    }
}
