package com.expertiz.sentinel.core.infrastructure.internal;

import com.expertiz.sentinel.core.shared.problem.ConstraintMapping;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contraintes d'{@code infrastructure} traduites en erreurs de champ (spec §9.1, §11). */
@Configuration(proxyBeanMethods = false)
class InfrastructureConstraintMappings {

    /** Nom unique parmi les VPS non supprimés, sans tenir compte de la casse (Review Focus 1). */
    @Bean
    ConstraintMapping uniqueActiveServerName() {
        return new ConstraintMapping("uq_servers_name_active", ProblemType.DUPLICATE, "name");
    }
}
