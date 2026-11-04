package com.expertiz.sentinel.core.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void generatesDistinctVersion7Uuids() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            UUID id = UuidV7.next();
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
            ids.add(id);
        }
        assertThat(ids).hasSize(1000);
    }
}
