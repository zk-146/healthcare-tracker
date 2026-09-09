package com.healthcare.activitytracker.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class StartupSecretValidatorTest {

  private static final String ACCESS_SECRET = "a-real-access-secret-value-of-sufficient-length";
  private static final String REFRESH_SECRET = "a-real-refresh-secret-value-of-sufficient-length";

  private static MockEnvironment environment(String... profiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    return env;
  }

  private static GoogleHealthProperties googleHealth(boolean enabled, String tokenKey) {
    GoogleHealthProperties properties = new GoogleHealthProperties();
    properties.setEnabled(enabled);
    properties.setTokenEncryptionKey(tokenKey);
    return properties;
  }

  private static void run(
      String accessSecret,
      String refreshSecret,
      GoogleHealthProperties googleHealth,
      String... profiles) {
    new StartupSecretValidator(accessSecret, refreshSecret, googleHealth, environment(profiles))
        .run(new DefaultApplicationArguments());
  }

  @Test
  void acceptsDistinctRealSecretsInProd() {
    assertThatCode(
            () ->
                run(
                    ACCESS_SECRET,
                    REFRESH_SECRET,
                    googleHealth(false, GoogleHealthProperties.DEFAULT_TOKEN_ENCRYPTION_KEY),
                    "prod"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDefaultJwtSecretInProd() {
    assertThatThrownBy(
            () ->
                run(
                    "default-dev-secret-key-change-in-production-must-be-at-least-32-chars",
                    REFRESH_SECRET,
                    googleHealth(false, "irrelevant"),
                    "prod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT secrets");
  }

  @Test
  void rejectsIdenticalAccessAndRefreshSecretsInProd() {
    assertThatThrownBy(
            () -> run(ACCESS_SECRET, ACCESS_SECRET, googleHealth(false, "irrelevant"), "prod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("identical");
  }

  @Test
  void rejectsDefaultGoogleTokenKeyInProdWhenIntegrationEnabled() {
    assertThatThrownBy(
            () ->
                run(
                    ACCESS_SECRET,
                    REFRESH_SECRET,
                    googleHealth(true, GoogleHealthProperties.DEFAULT_TOKEN_ENCRYPTION_KEY),
                    "prod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GOOGLE_HEALTH_TOKEN_KEY");
  }

  @Test
  void allowsDefaultGoogleTokenKeyInProdWhenIntegrationDisabled() {
    assertThatCode(
            () ->
                run(
                    ACCESS_SECRET,
                    REFRESH_SECRET,
                    googleHealth(false, GoogleHealthProperties.DEFAULT_TOKEN_ENCRYPTION_KEY),
                    "prod"))
        .doesNotThrowAnyException();
  }

  @Test
  void allowsRealGoogleTokenKeyInProdWhenIntegrationEnabled() {
    assertThatCode(
            () ->
                run(
                    ACCESS_SECRET,
                    REFRESH_SECRET,
                    googleHealth(true, "a-real-token-encryption-key"),
                    "prod"))
        .doesNotThrowAnyException();
  }

  @Test
  void onlyWarnsOutsideProd() {
    assertThatCode(
            () ->
                run(
                    "default-dev-secret-key-change-in-production-must-be-at-least-32-chars",
                    "default-dev-refresh-secret-change-in-production-at-least-32-chars",
                    googleHealth(true, GoogleHealthProperties.DEFAULT_TOKEN_ENCRYPTION_KEY)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsALongButDegenerateSecretInProd() {
    // Long enough for hmacShaKeyFor, but a single repeated character.
    String padding = "a".repeat(64);

    assertThatThrownBy(
            () -> run(padding, REFRESH_SECRET, googleHealth(false, "irrelevant"), "prod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("distinct characters");
  }

  @Test
  void acceptsAGeneratedLookingSecretInProd() {
    assertThatCode(
            () ->
                run(
                    "Xq7f2Kd9wPzR4tYbN1mHjL8sVcE6gA3u",
                    "Zr5nT8vQ2yWkM4pJ7bXcD1hF9sG6aL0e",
                    googleHealth(false, "irrelevant"),
                    "prod"))
        .doesNotThrowAnyException();
  }
}
