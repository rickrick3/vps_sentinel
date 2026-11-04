package com.expertiz.sentinel.core.shared.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.expertiz.sentinel.core.shared.web.CorrelationIdFilter;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ProblemWriterTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void writesProblemJsonForFiltersAndSecurityHandlers() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1");
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        var response = new MockHttpServletResponse();

        new ProblemWriter(json).write(request, response, ProblemType.ORIGIN_NOT_ALLOWED, "Origine non autorisée.");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = json.readValue(response.getContentAsString(), Map.class);
        assertThat(body)
                .containsEntry("type", "urn:sentinel:problem:origin-not-allowed")
                .containsEntry("title", "Origine non autorisée")
                .containsEntry("status", 403)
                .containsEntry("detail", "Origine non autorisée.")
                .containsEntry("instance", "/api/v1/auth/login")
                .containsEntry("correlationId", "corr-1");
    }
}
