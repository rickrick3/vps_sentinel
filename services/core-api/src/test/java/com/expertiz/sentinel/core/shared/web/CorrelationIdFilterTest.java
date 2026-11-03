package com.expertiz.sentinel.core.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /** Exécute le filtre et renvoie le correlationId vu dans le MDC pendant la requête. */
    private String run(MockHttpServletRequest request) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY)));
        return seen.get();
    }

    @Test
    void reusesValidIncomingId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "abc123");

        assertThat(run(request)).isEqualTo("abc123");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc123");
    }

    @Test
    void generatesWhenMissing() throws Exception {
        String id = run(new MockHttpServletRequest());

        assertThat(id).matches("[0-9a-f]{32}");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 65 caractères
        "abc 123",
        "abc;rm",
        ""
    })
    void replacesInvalidId(String incoming) throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", incoming);

        String id = run(request);

        assertThat(id).matches("[0-9a-f]{32}").isNotEqualTo(incoming);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(id);
    }

    @Test
    void errorDispatchKeepsTheRequestId() throws Exception {
        var request = new MockHttpServletRequest();
        String id = run(request);

        // Dispatch d'erreur du conteneur, après la fin du traitement : même requête, MDC déjà vidé.
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/me");
        assertThat(run(request)).isEqualTo(id);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(id);
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        run(new MockHttpServletRequest());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
