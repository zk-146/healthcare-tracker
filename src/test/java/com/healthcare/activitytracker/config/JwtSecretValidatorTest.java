package com.healthcare.activitytracker.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

class JwtSecretValidatorTest {

  private static final String DEFAULT_SECRET =
      "default-dev-secret-key-change-in-production-must-be-at-least-32-chars";
  private static final String DEFAULT_REFRESH =
      "default-dev-refresh-secret-change-in-production-at-least-32-chars";
  private static final String CUSTOM_SECRET =
      "a-properly-configured-production-secret-value-32+chars";

  private final ApplicationArguments args = mock(ApplicationArguments.class);

  private Environment envWithProfiles(String... profiles) {
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(profiles);
    return env;
  }

  @Test
  void defaultSecrets_withNoActiveProfile_refusesToStart() {
    JwtSecretValidator validator =
        new JwtSecretValidator(DEFAULT_SECRET, DEFAULT_REFRESH, envWithProfiles());

    assertThatThrownBy(() -> validator.run(args))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FATAL");
  }

  @Test
  void defaultSecrets_withProdProfile_refusesToStart() {
    JwtSecretValidator validator =
        new JwtSecretValidator(DEFAULT_SECRET, DEFAULT_REFRESH, envWithProfiles("prod"));

    assertThatThrownBy(() -> validator.run(args)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void defaultSecrets_withDevProfile_startsWithWarning() {
    JwtSecretValidator validator =
        new JwtSecretValidator(DEFAULT_SECRET, DEFAULT_REFRESH, envWithProfiles("dev"));

    assertThatCode(() -> validator.run(args)).doesNotThrowAnyException();
  }

  @Test
  void defaultSecrets_withTestProfile_startsWithWarning() {
    JwtSecretValidator validator =
        new JwtSecretValidator(DEFAULT_SECRET, DEFAULT_REFRESH, envWithProfiles("test"));

    assertThatCode(() -> validator.run(args)).doesNotThrowAnyException();
  }

  @Test
  void customSecrets_withNoProfile_startsCleanly() {
    JwtSecretValidator validator =
        new JwtSecretValidator(CUSTOM_SECRET, CUSTOM_SECRET + "-refresh", envWithProfiles());

    assertThatCode(() -> validator.run(args)).doesNotThrowAnyException();
  }

  @Test
  void onlyOneDefaultSecret_withNoProfile_refusesToStart() {
    JwtSecretValidator validator =
        new JwtSecretValidator(DEFAULT_SECRET, CUSTOM_SECRET + "-refresh", envWithProfiles());

    assertThatThrownBy(() -> validator.run(args)).isInstanceOf(IllegalStateException.class);
  }
}
