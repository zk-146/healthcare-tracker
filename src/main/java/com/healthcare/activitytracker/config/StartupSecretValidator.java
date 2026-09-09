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

/**
 * Fails startup in the {@code prod} profile when a secret is still set to a value that ships in
 * this repository, and warns about it everywhere else.
 *
 * <p>Covers every secret the application derives a key from: the two JWT signing secrets and the
 * passphrase that encrypts stored Google OAuth tokens at rest.
 */
@Component
public class StartupSecretValidator implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(StartupSecretValidator.class);

  private static final Set<String> INSECURE_DEFAULTS =
      Set.of(
          "default-dev-secret-key-change-in-production-must-be-at-least-32-chars",
          "default-dev-refresh-secret-change-in-production-at-least-32-chars");

  private final String jwtSecret;
  private final String jwtRefreshSecret;
  private final GoogleHealthProperties googleHealthProperties;
  private final Environment environment;

  public StartupSecretValidator(
      @Value("${app.jwt.secret}") String jwtSecret,
      @Value("${app.jwt.refresh-secret}") String jwtRefreshSecret,
      GoogleHealthProperties googleHealthProperties,
      Environment environment) {
    this.jwtSecret = jwtSecret;
    this.jwtRefreshSecret = jwtRefreshSecret;
    this.googleHealthProperties = googleHealthProperties;
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
      failOrWarn(isProduction, message);
    }

    // Identical secrets collapse the access/refresh key separation: a refresh token would
    // carry a valid signature under the access key (only the type claim would stop it).
    if (jwtSecret.equals(jwtRefreshSecret)) {
      failOrWarn(
          isProduction,
          "JWT_SECRET and JWT_REFRESH_SECRET are identical. Access and refresh tokens must be "
              + "signed with different keys.");
    }

    validateGoogleHealthTokenKey(isProduction);
  }

  /**
   * The Google OAuth token-encryption passphrase only matters when the integration is switched on —
   * with {@code enabled=false} nothing is ever encrypted with it, so the shipped default stays
   * harmless and must not block startup.
   */
  private void validateGoogleHealthTokenKey(boolean isProduction) {
    if (!googleHealthProperties.isEnabled()) {
      return;
    }
    if (GoogleHealthProperties.DEFAULT_TOKEN_ENCRYPTION_KEY.equals(
        googleHealthProperties.getTokenEncryptionKey())) {
      failOrWarn(
          isProduction,
          "The Google Health integration is enabled but its token-encryption key is still the "
              + "default that ships in this repository, so stored OAuth tokens are effectively "
              + "unencrypted. Set GOOGLE_HEALTH_TOKEN_KEY.");
    }
  }

  private void failOrWarn(boolean isProduction, String message) {
    if (isProduction) {
      throw new IllegalStateException(
          "FATAL: " + message + " Application cannot start in 'prod' profile.");
    }
    log.warn("WARNING: {} This is acceptable for local development only.", message);
  }
}
