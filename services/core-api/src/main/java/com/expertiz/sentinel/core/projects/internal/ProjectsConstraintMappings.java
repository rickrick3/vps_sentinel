package com.expertiz.sentinel.core.projects.internal;

import com.expertiz.sentinel.core.shared.problem.ConstraintMapping;
import com.expertiz.sentinel.core.shared.problem.ProblemType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contraintes de {@code projects} traduites en erreurs de champ (spec §9.1, §11). */
@Configuration(proxyBeanMethods = false)
class ProjectsConstraintMappings {

    @Bean
    ConstraintMapping uniqueActiveProjectName() {
        return new ConstraintMapping("uq_projects_name_active", ProblemType.DUPLICATE, "name");
    }

    @Bean
    ConstraintMapping uniqueActiveEnvironmentName() {
        return new ConstraintMapping("uq_environments_project_name_active", ProblemType.DUPLICATE, "name");
    }

    /** Un hostname n'appartient qu'à un seul environnement, tous projets confondus. */
    @Bean
    ConstraintMapping uniqueDomainHostname() {
        return new ConstraintMapping("uq_domains_hostname", ProblemType.DUPLICATE, "hostname");
    }

    @Bean
    ConstraintMapping uniqueDeployment() {
        return new ConstraintMapping("uq_deployments_env_server", ProblemType.DEPLOYMENT_EXISTS, "serverId");
    }
}
