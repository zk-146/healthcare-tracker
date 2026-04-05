package com.healthcare.activitytracker.config;

import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class JwtSecretValidator implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);

  private static final Set<String> INSECURE_DEFAULTS =
      Set.of(
          "default-dev-secret-key-change-in-production-must-be-at-least-32-chars",
          "default-dev-refresh-secret-change-in-production-at-least-32-chars");

  private final String jwtSecret;
  private final String jwtRefreshSecret;
  private final Environment environment;

  public JwtSecretValidator(
      @Value("${app.jwt.secret}") String jwtSecret,
      @Value("${app.jwt.refresh-secret}") String jwtRefreshSecret,
      Environment environment) {
    this.jwtSecret = jwtSecret;
    this.jwtRefreshSecret = jwtRefreshSecret;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod");
    boolean hasInsecureSecret = INSECURE_DEFAULTS.contains(jwtSecret);
    boolean hasInsecureRefresh = INSECURE_DEFAULTS.contains(jwtRefreshSecret);

    if (hasInsecureSecret || hasInsecureRefresh) {
      String message =
          "JWT secrets are set to insecure default values. "
              + "Set JWT_SECRET and JWT_REFRESH_SECRET environment variables.";
      if (isProduction) {
        throw new IllegalStateException(
            "FATAL: "
                + message
                + " Application cannot start in 'prod' profile with default secrets.");
      }
      log.warn("WARNING: {} This is acceptable for local development only.", message);
    }
  }
}
