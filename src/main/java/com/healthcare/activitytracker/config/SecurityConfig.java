package com.healthcare.activitytracker.config;

import com.healthcare.activitytracker.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Value("${app.cors.allowed-origins:http://localhost:3000}")
  private String allowedOrigins;

  private final Environment environment;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, Environment environment) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.environment = environment;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // CSRF is disabled because this API uses stateless JWT Bearer tokens (no cookies).
        // CodeQL flags this anyway; see .github/codeql/codeql-config.yml for the exclusion.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers -> {
              headers.httpStrictTransportSecurity(
                  hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000).preload(true));
              headers.contentTypeOptions(contentType -> {});
              headers.frameOptions(frame -> frame.deny());
              headers.referrerPolicy(
                  referrer ->
                      referrer.policy(
                          org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                              .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
              headers.contentSecurityPolicy(
                  csp ->
                      csp.policyDirectives(
                          "default-src 'self'; img-src 'self' data:; connect-src 'self'; "
                              + "frame-ancestors 'none'; base-uri 'none'; form-action 'none'"));
              headers.permissionsPolicy(
                  permissions -> permissions.policy("geolocation=(), camera=(), microphone=()"));
            })
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        // Account recovery: by definition the caller cannot log in. Each is
                        // authorised by a single-use emailed token, or (forgot-password) does
                        // nothing visible to the caller at all.
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/verify-email")
                    .permitAll()
                    // OAuth redirect target: Google sends the browser here with no JWT, so it
                    // cannot require authentication. It is secured instead by the one-time state.
                    .requestMatchers("/api/v1/integrations/google-health/callback")
                    .permitAll()
                    // SPA shell and hashed build assets. Authentication is enforced per-request by
                    // JwtAuthenticationFilter on /api/**, not by withholding the HTML.
                    .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**")
                    .permitAll()
                    // API docs are public; springdoc is disabled in the prod profile,
                    // where these paths return 404
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // Health (and its liveness/readiness probes) is public; it reports status
                    // only, never details.
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness")
                    .permitAll()
                    // Metrics are served only over a dedicated management port, which is not
                    // published to the outside. On the application port this does not match
                    // and the denyAll below applies.
                    .requestMatchers(this::isMetricsScrapeOnManagementPort)
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) -> {
                      response.setStatus(401);
                      response.setContentType("application/json");
                      response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
                    }))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * True for {@code GET /actuator/prometheus} arriving on a management port that is separate from
   * the application port.
   *
   * <p>Metrics carry no credentials of their own, so reachability is the access control: the
   * management port is bound for the orchestrator and Prometheus and is never published. When no
   * separate port is configured ({@code MANAGEMENT_PORT} unset) there is nothing to tell a scrape
   * from a request off the internet, so this never matches and metrics stay closed.
   *
   * <p>The ports are read per request from the {@code local.*.port} properties Boot publishes once
   * each web server has started, rather than from configuration, so a configured port of 0 (random,
   * as in tests) resolves to the port actually bound.
   */
  private boolean isMetricsScrapeOnManagementPort(HttpServletRequest request) {
    if (!"/actuator/prometheus".equals(request.getRequestURI())) {
      return false;
    }
    Integer managementPort = environment.getProperty("local.management.port", Integer.class);
    Integer serverPort = environment.getProperty("local.server.port", Integer.class);
    return managementPort != null
        && !managementPort.equals(serverPort)
        && request.getLocalPort() == managementPort;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList());
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    // X-User-Timezone is read by the summary endpoints for streak boundary calculations
    config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-User-Timezone"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  /**
   * Cost 12 rather than Spring's default of 10 — roughly four times the work per guess, which is
   * the margin worth having on a store of health records.
   *
   * <p>Raising it is backward compatible: BCrypt encodes the cost it used inside the hash, so
   * existing strength-10 hashes keep verifying and are re-hashed at the new cost the next time a
   * password is set.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
