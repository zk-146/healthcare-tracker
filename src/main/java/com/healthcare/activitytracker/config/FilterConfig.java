package com.healthcare.activitytracker.config;

import com.healthcare.activitytracker.filter.JwtAuthenticationFilter;
import com.healthcare.activitytracker.filter.RateLimitingFilter;
import com.healthcare.activitytracker.filter.RequestLoggingFilter;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit servlet-filter registrations.
 *
 * <p>The Spring Security filter chain is registered at order {@link
 * SecurityProperties#DEFAULT_FILTER_ORDER} (-100), so plain {@code @Order} values like 0/1/2 on
 * filter beans would place them <em>after</em> security — requests rejected by the security layer
 * would be neither rate-limited nor logged. Rate limiting and request logging are therefore
 * registered explicitly ahead of the security chain.
 *
 * <p>{@link JwtAuthenticationFilter} participates only in the security chain (added in {@link
 * SecurityConfig}); its automatic servlet-container registration is disabled here so it is not
 * registered twice.
 */
@Configuration
public class FilterConfig {

  private static final int RATE_LIMITING_FILTER_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 2;
  private static final int REQUEST_LOGGING_FILTER_ORDER =
      SecurityProperties.DEFAULT_FILTER_ORDER - 1;

  @Bean
  public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(
      RateLimitingFilter filter) {
    FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(RATE_LIMITING_FILTER_ORDER);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
      RequestLoggingFilter filter) {
    FilterRegistrationBean<RequestLoggingFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setOrder(REQUEST_LOGGING_FILTER_ORDER);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
      JwtAuthenticationFilter filter) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
