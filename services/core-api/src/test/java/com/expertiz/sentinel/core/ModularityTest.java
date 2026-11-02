package com.expertiz.sentinel.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(CoreApiApplication.class);

    @Test
    void modulesRespectBoundaries() {
        MODULES.verify();
    }

    @Test
    void expectedModulesExist() {
        assertThat(MODULES.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .containsExactlyInAnyOrder("shared", "identity", "audit", "infrastructure", "projects");
    }
}
