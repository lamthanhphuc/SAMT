package com.example.reportservice.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void shouldGenerateAndPropagateRequestIdWhenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reports");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idInChain = new AtomicReference<>();

        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                idInChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        });

        filter.doFilter(request, response, chain);

        String responseHeader = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(responseHeader).isNotBlank();
        assertThat(idInChain.get()).isEqualTo(responseHeader);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void shouldReuseIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reports");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("req-123");
    }
}
