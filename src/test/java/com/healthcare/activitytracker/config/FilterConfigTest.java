package com.healthcare.activitytracker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.healthcare.activitytracker.filter.JwtAuthenticationFilter;
import com.healthcare.activitytracker.filter.RateLimitingFilter;
import com.healthcare.activitytracker.filter.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class FilterConfigTest {

  private final FilterConfig filterConfig = new FilterConfig();

  @Test
  void rateLimitingFilter_isRegisteredAheadOfSecurity() {
    RateLimitingFilter filter = mock(RateLimitingFilter.class);

    FilterRegistrationBean<RateLimitingFilter> registration =
        filterConfig.rateLimitingFilterRegistration(filter);

    assertThat(registration.getFilter()).isSameAs(filter);
    assertThat(registration.getOrder()).isEqualTo(SecurityProperties.DEFAULT_FILTER_ORDER - 2);
  }

  @Test
  void requestLoggingFilter_isRegisteredAheadOfSecurityButAfterRateLimiting() {
    RequestLoggingFilter filter = mock(RequestLoggingFilter.class);

    FilterRegistrationBean<RequestLoggingFilter> registration =
        filterConfig.requestLoggingFilterRegistration(filter);

    assertThat(registration.getFilter()).isSameAs(filter);
    assertThat(registration.getOrder()).isEqualTo(SecurityProperties.DEFAULT_FILTER_ORDER - 1);
    assertThat(registration.getOrder())
        .isGreaterThan(
            filterConfig.rateLimitingFilterRegistration(mock(RateLimitingFilter.class)).getOrder());
  }

  @Test
  void jwtAuthenticationFilter_servletRegistrationIsDisabled() {
    JwtAuthenticationFilter filter = mock(JwtAuthenticationFilter.class);

    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        filterConfig.jwtAuthenticationFilterRegistration(filter);

    // It runs only inside the Spring Security chain (registered separately in SecurityConfig);
    // disabling the container-level registration prevents it from running twice per request.
    assertThat(registration.isEnabled()).isFalse();
    assertThat(registration.getFilter()).isSameAs(filter);
  }
}
