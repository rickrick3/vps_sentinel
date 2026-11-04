package com.expertiz.sentinel.core.infrastructure.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.expertiz.sentinel.core.shared.problem.ApiException;
import com.expertiz.sentinel.core.shared.problem.FieldViolation;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tags d'un VPS (spec §10.3) : minuscules, dédoublonnés, triés ; 1 à 50 caractères {@code [a-z0-9._-]} ; 20 au plus. */
class TagRulesTest {

    private static FieldViolation rejected(List<String> raw) {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> TagRules.normalize(raw));
        assertThat(ex).as("refus attendu pour %s", raw).isNotNull();
        assertThat(ex.type()).isEqualTo(ProblemType.VALIDATION_ERROR);
        assertThat(ex.errors()).hasSize(1);
        assertThat(ex.errors().getFirst().field()).isEqualTo("tags");
        return ex.errors().getFirst();
    }

    @Test
    void normalizes() {
        assertThat(TagRules.normalize(List.of(" Prod ", "prod", "WEB"))).containsExactly("prod", "web");
    }

    @Test
    void absentMeansNoTags() {
        assertThat(TagRules.normalize(null)).isEmpty();
        assertThat(TagRules.normalize(List.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a b", "-prod", "_x", ".x", "été", "tag/1", "", "   "})
    void rejectsInvalidTag(String tag) {
        rejected(List.of("ok", tag));
    }

    @Test
    void rejectsNullEntry() {
        rejected(Arrays.asList("ok", null));
    }

    @Test
    void accepts50RejectsLonger() {
        assertThat(TagRules.normalize(List.of("a".repeat(50)))).hasSize(1);
        rejected(List.of("a".repeat(51)));
    }

    @Test
    void accepts20RejectsMore() {
        List<String> twenty = IntStream.range(0, 20).mapToObj(i -> "tag" + i).toList();
        assertThat(TagRules.normalize(twenty)).hasSize(20);

        List<String> twentyOne = new ArrayList<>(twenty);
        twentyOne.add("tag20");
        assertThat(rejected(twentyOne).message()).isEqualTo("20 tags maximum");
    }

    @Test
    void duplicatesCountOnce() {
        List<String> raw = new ArrayList<>(IntStream.range(0, 20).mapToObj(i -> "tag" + i).toList());
        raw.addAll(List.of("TAG1", " tag2 ", "Tag3"));

        assertThat(TagRules.normalize(raw)).hasSize(20);
    }
}
