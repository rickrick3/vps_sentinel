package com.expertiz.sentinel.core.identity.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RoleCodeTest {

    @Test
    void patternListsExactlyTheRoles() {
        assertThat(RoleCode.PATTERN.split("\\|")).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(RoleCode.values()).map(Enum::name).toList());
    }
}
