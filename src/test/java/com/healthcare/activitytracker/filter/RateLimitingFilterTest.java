package com.healthcare.activitytracker.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class RateLimitingFilterTest {

  private RateLimitingFilter filter;

  @BeforeEach
  void setUp() {
    filter = new RateLimitingFilter(null);
    ReflectionTestUtils.setField(filter, "authRequestsPerMinute", 10);
    ReflectionTestUtils.setField(filter, "apiRequestsPerMinute", 60);
    ReflectionTestUtils.setField(filter, "trustedProxiesConfig", "");
    filter.init();
  }

  @Test
  void nonApiRequest_passesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void apiRequest_withinLimit_passesThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/activities");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
  }

  @Test
  void apiRequest_exceedingLimit_returns429() throws Exception {
    // Set a very tight limit of 1 request per minute
    RateLimitingFilter tightFilter = new RateLimitingFilter(null);
    ReflectionTestUtils.setField(tightFilter, "authRequestsPerMinute", 10);
    ReflectionTestUtils.setField(tightFilter, "apiRequestsPerMinute", 1);
    ReflectionTestUtils.setField(tightFilter, "trustedProxiesConfig", "");
    tightFilter.init();

    String ip = "10.0.0.1";
    FilterChain chain = mock(FilterChain.class);

    // First request — consumed the single token
    MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/activities");
    req1.setRemoteAddr(ip);
    MockHttpServletResponse res1 = new MockHttpServletResponse();
    tightFilter.doFilterInternal(req1, res1, chain);
    assertThat(res1.getStatus()).isEqualTo(200);

    // Second request — bucket empty, should be rate-limited
    MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/activities");
    req2.setRemoteAddr(ip);
    MockHttpServletResponse res2 = new MockHttpServletResponse();
    tightFilter.doFilterInternal(req2, res2, chain);
    assertThat(res2.getStatus()).isEqualTo(429);
    assertThat(res2.getHeader("Retry-After")).isEqualTo("60");
  }

  @Test
  void authEndpoint_exceedingAuthLimit_returns429() throws Exception {
    RateLimitingFilter tightFilter = new RateLimitingFilter(null);
    ReflectionTestUtils.setField(tightFilter, "authRequestsPerMinute", 1);
    ReflectionTestUtils.setField(tightFilter, "apiRequestsPerMinute", 60);
    ReflectionTestUtils.setField(tightFilter, "trustedProxiesConfig", "");
    tightFilter.init();

    String ip = "10.0.0.2";
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    req1.setRemoteAddr(ip);
    MockHttpServletResponse res1 = new MockHttpServletResponse();
    tightFilter.doFilterInternal(req1, res1, chain);
    assertThat(res1.getStatus()).isEqualTo(200);

    MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    req2.setRemoteAddr(ip);
    MockHttpServletResponse res2 = new MockHttpServletResponse();
    tightFilter.doFilterInternal(req2, res2, chain);
    assertThat(res2.getStatus()).isEqualTo(429);
  }

  @Test
  void authEndpoint_doesNotConsumeGeneralApiBudget() throws Exception {
    // api budget of exactly 1; an auth request must NOT draw from it.
    RateLimitingFilter f = new RateLimitingFilter(null);
    ReflectionTestUtils.setField(f, "authRequestsPerMinute", 10);
    ReflectionTestUtils.setField(f, "apiRequestsPerMinute", 1);
    ReflectionTestUtils.setField(f, "trustedProxiesConfig", "");
    f.init();

    String ip = "10.0.0.9";
    FilterChain chain = mock(FilterChain.class);

    // An auth call should consume only the auth bucket.
    MockHttpServletRequest authReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    authReq.setRemoteAddr(ip);
    MockHttpServletResponse authRes = new MockHttpServletResponse();
    f.doFilterInternal(authReq, authRes, chain);
    assertThat(authRes.getStatus()).isEqualTo(200);

    // The single API token is still available afterwards.
    MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/api/v1/activities");
    apiReq.setRemoteAddr(ip);
    MockHttpServletResponse apiRes = new MockHttpServletResponse();
    f.doFilterInternal(apiReq, apiRes, chain);
    assertThat(apiRes.getStatus()).isEqualTo(200);
  }

  @Test
  void xForwardedFor_notTrusted_whenNoProxyConfigured() throws Exception {
    // With no trusted proxies, X-Forwarded-For must be ignored.
    // Two requests from the same "real" IP (even with different X-Forwarded-For headers)
    // should share the same bucket.
    RateLimitingFilter tightFilter = new RateLimitingFilter(null);
    ReflectionTestUtils.setField(tightFilter, "authRequestsPerMinute", 10);
    ReflectionTestUtils.setField(tightFilter, "apiRequestsPerMinute", 1);
    ReflectionTestUtils.setField(tightFilter, "trustedProxiesConfig", "");
    tightFilter.init();

    FilterChain chain = mock(FilterChain.class);

    // Both requests come from the same real IP but claim different client IPs
    MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/activities");
    req1.setRemoteAddr("192.168.1.1");
    req1.addHeader("X-Forwarded-For", "1.2.3.4");
    MockHttpServletResponse res1 = new MockHttpServletResponse();
    tightFilter.doFilterInternal(req1, res1, chain);
    assertThat(res1.getStatus()).isEqualTo(200);

    // Second request from same real IP — should share bucket and be limited
    MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/activities");
    req2.setRemoteAddr("192.168.1.1");
    req2.addHeader("X-Forwarded-For", "5.6.7.8");
    MockHttpServletResponse res2 = new MockHttpServletResponse();
    tightFilter.doFilterInternal(req2, res2, chain);
    assertThat(res2.getStatus()).isEqualTo(429);
  }

  @Test
  void xForwardedFor_trusted_whenProxyConfigured() throws Exception {
    RateLimitingFilter proxyFilter = new RateLimitingFilter(null);
    ReflectionTestUtils.setField(proxyFilter, "authRequestsPerMinute", 10);
    ReflectionTestUtils.setField(proxyFilter, "apiRequestsPerMinute", 60);
    ReflectionTestUtils.setField(proxyFilter, "trustedProxiesConfig", "10.0.0.1");
    proxyFilter.init();

    FilterChain chain = mock(FilterChain.class);

    // Request arrives via trusted proxy with a real client IP in X-Forwarded-For
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/activities");
    request.setRemoteAddr("10.0.0.1");
    request.addHeader("X-Forwarded-For", "203.0.113.5");
    MockHttpServletResponse response = new MockHttpServletResponse();
    proxyFilter.doFilterInternal(request, response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
