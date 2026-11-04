package com.expertiz.sentinel.core.shared.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.shared.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ProblemDetailsAdviceTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ProblemDetailsAdviceTest.TestController.class, ProblemDetailsAdviceTest.Mappings.class})
class ProblemDetailsAdviceTest {

    @Autowired
    MockMvcTester mvc;

    // Les filtres sont désactivés : le correlationId que le filtre placerait dans le MDC est posé ici.
    @BeforeEach
    void correlation() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-test");
    }

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void apiExceptionRendered() {
        MvcTestResult result = mvc.get().uri("/test/problem").exchange();

        assertThat(result).hasStatus(409).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:server-in-use");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("VPS utilisé");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("VPS utilisé par des déploiements");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/test/problem");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("corr-test");
    }

    @Test
    void fieldErrorsListed() {
        MvcTestResult result = mvc.post()
                .uri("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:validation-error");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("ne doit pas être vide");
    }

    @Test
    void unreadableBodyNotEchoed() {
        MvcTestResult result = mvc.post()
                .uri("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"S3cr3t-Passw0rd!")
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:validation-error");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Corps de requête invalide.");
        assertThat(result).bodyText().doesNotContain("S3cr3t");
    }

    @Test
    void textPlainIs415() {
        MvcTestResult result = mvc.post()
                .uri("/test/validated")
                .contentType(MediaType.TEXT_PLAIN)
                .content("name=x")
                .exchange();

        assertThat(result).hasStatus(415);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:unsupported-media-type");
    }

    @Test
    void unexpectedErrorHasNoDetails() {
        MvcTestResult result = mvc.get().uri("/test/boom").exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:internal-error");
        assertThat(result).bodyText().doesNotContain("boom-secret").doesNotContain("RuntimeException");
    }

    @Test
    void optimisticLockIsVersionConflict() {
        MvcTestResult result = mvc.get().uri("/test/optimistic").exchange();

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:version-conflict");
    }

    @Test
    void uniqueViolationMapsToField() {
        MvcTestResult result = mvc.get().uri("/test/duplicate").exchange();

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:duplicate");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
        assertThat(result).bodyText().doesNotContain("VPS-01");
    }

    @Test
    void unmappedConstraintIsInternalError() {
        MvcTestResult result = mvc.get().uri("/test/unmapped-constraint").exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:internal-error");
    }

    @Test
    void accessDeniedIs403NotInternalError() {
        MvcTestResult result = mvc.get().uri("/test/denied").exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:forbidden");
    }

    @Test
    void unknownPathIs404() {
        MvcTestResult result = mvc.get().uri("/test/nowhere").exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:not-found");
    }

    @Test
    void wrongMethodIs405() {
        MvcTestResult result = mvc.delete().uri("/test/problem").exchange();

        assertThat(result).hasStatus(405).hasHeader("Allow", "GET");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:sentinel:problem:method-not-allowed");
    }

    record Named(@NotBlank String name) {}

    @RestController
    static class TestController {

        @GetMapping("/test/problem")
        void problem() {
            throw new ApiException(ProblemType.SERVER_IN_USE, "VPS utilisé par des déploiements");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody Named body) {}

        @GetMapping("/test/boom")
        void boom() {
            throw new RuntimeException("boom-secret");
        }

        @GetMapping("/test/optimistic")
        void optimistic() {
            throw new ObjectOptimisticLockingFailureException(Object.class, "id");
        }

        @GetMapping("/test/duplicate")
        void duplicate() {
            throw uniqueViolation("uq_test_name");
        }

        @GetMapping("/test/unmapped-constraint")
        void unmappedConstraint() {
            throw uniqueViolation("uq_not_declared");
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("denied");
        }

        private static DataIntegrityViolationException uniqueViolation(String constraint) {
            ServerErrorMessage message = new ServerErrorMessage("SERROR\0C23505\0Mduplicate key value violates unique constraint \""
                    + constraint + "\"\0DKey (name)=(VPS-01) already exists.\0n" + constraint + "\0");
            return new DataIntegrityViolationException("could not execute statement", new PSQLException(message));
        }
    }

    @TestConfiguration
    static class Mappings {

        @Bean
        ConstraintMapping testName() {
            return new ConstraintMapping("uq_test_name", ProblemType.DUPLICATE, "name");
        }
    }
}
