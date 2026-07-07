package com.healthcare.activitytracker.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs one summary line per request and owns the MDC lifecycle: it assigns the {@code requestId} on
 * entry and clears the MDC on exit. It is registered ahead of the Spring Security chain (see {@code
 * FilterConfig}), so security rejections are logged too and MDC entries contributed by inner
 * filters (e.g. {@code userId} from {@code JwtAuthenticationFilter}) are still present when the
 * summary line is written.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  // Authorization and other sensitive headers are intentionally excluded from all log statements
  // to comply with HIPAA and healthcare data handling requirements.

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    long start = System.currentTimeMillis();
    MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));

    try {
      filterChain.doFilter(request, response);
    } finally {
      long duration = System.currentTimeMillis() - start;
      log.info(
          "{} {} {} - {}ms",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          duration);
      MDC.clear();
    }
  }
}
