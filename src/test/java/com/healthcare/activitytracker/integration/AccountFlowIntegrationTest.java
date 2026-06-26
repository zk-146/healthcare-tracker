package com.healthcare.activitytracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.dto.ActivityRequest;
import com.healthcare.activitytracker.model.dto.AuthResponse;
import com.healthcare.activitytracker.model.dto.ForgotPasswordRequest;
import com.healthcare.activitytracker.model.dto.LoginRequest;
import com.healthcare.activitytracker.model.dto.RegisterRequest;
import com.healthcare.activitytracker.model.dto.ResetPasswordRequest;
import com.healthcare.activitytracker.model.dto.UserDataExportResponse;
import com.healthcare.activitytracker.model.dto.VerifyEmailRequest;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.repository.AuditLogRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end happy path against a real PostgreSQL + Flyway stack: registration, email verification,
 * authenticated activity creation, data export, password reset, and account erasure. Proves the
 * production migrations (V1–V3) and the new compliance/recovery flows work against Postgres — not
 * just H2.
 */
class AccountFlowIntegrationTest extends AbstractIntegrationTest {

  private static final String EMAIL = "integration.user@example.com";
  private static final String ORIGINAL_PASSWORD = "OriginalP@ss123";
  private static final String NEW_PASSWORD = "BrandNewP@ss456";

  @Autowired private TestRestTemplate rest;
  @Autowired private RecordingEmailService email;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;

  @TestConfiguration
  static class EmailOverride {
    @Bean
    @Primary
    RecordingEmailService recordingEmailService() {
      return new RecordingEmailService();
    }
  }

  @Test
  void fullAccountLifecycle() {
    // 1. Register -> 201 + tokens, email starts unverified.
    RegisterRequest register = new RegisterRequest();
    register.setEmail(EMAIL);
    register.setPassword(ORIGINAL_PASSWORD);
    register.setFullName("Integration User");

    ResponseEntity<AuthResponse> registerResponse =
        rest.postForEntity("/api/v1/auth/register", register, AuthResponse.class);
    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(userRepository.findByEmail(EMAIL))
        .get()
        .satisfies(u -> assertThat(u.isEmailVerified()).isFalse());

    // 2. Verify email using the token captured by the recording email transport.
    String verificationToken = email.verificationTokenFor(EMAIL);
    assertThat(verificationToken).isNotBlank();
    VerifyEmailRequest verify = new VerifyEmailRequest();
    verify.setToken(verificationToken);
    ResponseEntity<Void> verifyResponse =
        rest.postForEntity("/api/v1/auth/verify-email", verify, Void.class);
    assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(userRepository.findByEmail(EMAIL))
        .get()
        .satisfies(u -> assertThat(u.isEmailVerified()).isTrue());

    // 3. Authenticated request: create an activity.
    String accessToken = registerResponse.getBody().getToken();
    ActivityRequest activity = new ActivityRequest();
    activity.setActivityType(ActivityType.RUNNING);
    activity.setSource(ActivitySource.MANUAL);
    activity.setStartedAt(LocalDateTime.now().minusHours(1));
    activity.setDurationMinutes(45);
    activity.setDistanceKm(5.0);

    ResponseEntity<String> createResponse =
        rest.exchange(
            "/api/v1/activities", HttpMethod.POST, authed(activity, accessToken), String.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // 4. Export contains the profile and the activity just created.
    ResponseEntity<UserDataExportResponse> exportResponse =
        rest.exchange(
            "/api/v1/profile/export",
            HttpMethod.GET,
            authed(null, accessToken),
            UserDataExportResponse.class);
    assertThat(exportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(exportResponse.getBody().getProfile().getEmail()).isEqualTo(EMAIL);
    assertThat(exportResponse.getBody().getActivities()).hasSize(1);

    // 5. Forgot + reset password, then confirm the new credentials work and old ones do not.
    ForgotPasswordRequest forgot = new ForgotPasswordRequest();
    forgot.setEmail(EMAIL);
    assertThat(
            rest.postForEntity("/api/v1/auth/forgot-password", forgot, Void.class).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    String resetToken = email.resetTokenFor(EMAIL);
    assertThat(resetToken).isNotBlank();
    ResetPasswordRequest reset = new ResetPasswordRequest();
    reset.setToken(resetToken);
    reset.setNewPassword(NEW_PASSWORD);
    assertThat(rest.postForEntity("/api/v1/auth/reset-password", reset, Void.class).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(login(NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login(ORIGINAL_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 6. Erase the account, using a token minted from the post-reset login.
    String freshToken = login(NEW_PASSWORD).getBody().getToken();
    ResponseEntity<Void> deleteResponse =
        rest.exchange("/api/v1/profile", HttpMethod.DELETE, authed(null, freshToken), Void.class);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(userRepository.findByEmail(EMAIL)).isEmpty();

    // The profile is gone even though the (stateless) JWT still authenticates.
    ResponseEntity<String> afterDelete =
        rest.exchange("/api/v1/profile", HttpMethod.GET, authed(null, freshToken), String.class);
    assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // The compliance audit trail recorded events throughout (persisted in Postgres).
    assertThat(auditLogRepository.count()).isPositive();
  }

  private ResponseEntity<AuthResponse> login(String password) {
    LoginRequest login = new LoginRequest();
    login.setEmail(EMAIL);
    login.setPassword(password);
    return rest.postForEntity("/api/v1/auth/login", login, AuthResponse.class);
  }

  private <T> HttpEntity<T> authed(T body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }
}
