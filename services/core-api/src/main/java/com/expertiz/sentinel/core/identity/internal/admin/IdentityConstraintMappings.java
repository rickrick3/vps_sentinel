package com.expertiz.sentinel.core.identity.internal.admin;

import com.expertiz.sentinel.core.shared.problem.ConstraintMapping;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contraintes d'{@code identity} traduites en erreurs de champ (spec §9.1, §11). */
@Configuration(proxyBeanMethods = false)
class IdentityConstraintMappings {

    /** Deux utilisateurs actifs ne partagent pas un email, même créés en même temps : 409 {@code duplicate} sur {@code email}. */
    @Bean
    ConstraintMapping uniqueActiveEmail() {
        return new ConstraintMapping("uq_users_email_active", ProblemType.DUPLICATE, "email");
    }
}
