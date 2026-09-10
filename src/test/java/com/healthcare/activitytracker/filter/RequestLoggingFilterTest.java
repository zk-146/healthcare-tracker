package com.healthcare.activitytracker.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RequestLoggingFilterTest {

  private final RequestLoggingFilter filter = new RequestLoggingFilter();

  @Test
  void doFilterInternal_invokesChainAndClearsMdcAfterward() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/activities");
    when(response.getStatus()).thenReturn(200);

    filter.doFilterInternal(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    // MDC is cleared in the finally block, so nothing from this request should leak.
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void doFilterInternal_clearsMdcEvenWhenChainThrows() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/v1/activities");
    when(response.getStatus()).thenReturn(500);
    doAnswer(
            invocation -> {
              throw new RuntimeException("downstream failure");
            })
        .when(chain)
        .doFilter(any(), any());

    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> filter.doFilterInternal(request, response, chain));

    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void doFilterInternal_assignsARequestIdWhileChainRuns() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/profile");
    when(response.getStatus()).thenReturn(200);

    String[] requestIdDuringChain = new String[1];
    FilterChain chain =
        (req, res) -> requestIdDuringChain[0] = MDC.get("requestId");

    filter.doFilterInternal(request, response, chain);

    assertThat(requestIdDuringChain[0]).isNotNull();
    assertThat(requestIdDuringChain[0]).hasSize(8);
  }
}
