package com.expertiz.sentinel.core.identity.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RoleCodeTest {

    /** Motif ancré {@code ^(A|B|…)$} : le contrat OpenAPI le reprend, et un motif JSON Schema n'est pas ancré. */
    @Test
    void patternListsExactlyTheRoles() {
        assertThat(RoleCode.PATTERN).startsWith("^(").endsWith(")$");
        String alternatives = RoleCode.PATTERN.substring(2, RoleCode.PATTERN.length() - 2);
        assertThat(alternatives.split("\\|")).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(RoleCode.values()).map(Enum::name).toList());
    }
}
