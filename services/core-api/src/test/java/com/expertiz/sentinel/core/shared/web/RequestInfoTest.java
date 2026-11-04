package com.expertiz.sentinel.core.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestInfoTest {

    @AfterEach
    void unbind() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockHttpServletRequest bind(String remoteAddr) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void clientIpFromRemoteAddress() {
        bind("10.1.2.3");

        assertThat(RequestInfo.clientIp()).contains("10.1.2.3");
    }

    @Test
    void ipv6IsCanonicalised() {
        bind("2001:DB8::1");

        assertThat(RequestInfo.clientIp()).contains("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void ipv6ScopeIsDropped() {
        bind("fe80::1%eth0");

        assertThat(RequestInfo.clientIp()).contains("fe80:0:0:0:0:0:0:1");
    }

    @Test
    void invalidAddressIsAbsent() {
        bind("not-an-ip");

        assertThat(RequestInfo.clientIp()).isEmpty();
    }

    @Test
    void outsideARequestEverythingIsAbsent() {
        assertThat(RequestInfo.clientIp()).isEmpty();
        assertThat(RequestInfo.userAgent()).isEmpty();
    }

    @Test
    void userAgentTruncatedTo255() {
        bind("10.1.2.3").addHeader("User-Agent", "x".repeat(300));

        assertThat(RequestInfo.userAgent()).hasValueSatisfying(ua -> assertThat(ua).hasSize(255));
    }
}
