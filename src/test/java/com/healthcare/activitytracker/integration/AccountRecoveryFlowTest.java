package com.healthcare.activitytracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.service.EmailSender;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Password reset and email verification through the real HTTP layer, security chain, services and
 * database. Only the mail transport is replaced, so the test can read the link out of the message
 * exactly as a user would.
 */
@SpringBootTest(
    properties = {
      "app.outbox.enabled=false",
      "app.ollama.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      // Otherwise startup blocks for ~30s while KafkaAdmin waits on a broker that is not there.
      "spring.kafka.admin.auto-create=false",
      // Each test sends several emails to one account in quick succession.
      "app.mail.resend-cooldown-seconds=0"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountRecoveryFlowTest {

  private static final String OLD_PASSWORD = "OldP@ssword123";
  private static final String NEW_PASSWORD = "NewP@ssword456";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean EmailSender emailSender;

  private ResultActions postJson(String path, Map<String, String> body) throws Exception {
    return mockMvc.perform(
        post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
  }

  /** Registers an account and returns its access token. */
  private String register(String email) throws Exception {
    String response =
        postJson(
                "/api/v1/auth/register",
                Map.of("email", email, "password", OLD_PASSWORD, "fullName", "Recovery Test"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode json = objectMapper.readTree(response);
    return json.get("token").asText();
  }

  private ResultActions login(String email, String password) throws Exception {
    return postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
  }

  /** The token in the most recent email to this address whose link uses the given fragment key. */
  private String tokenFromLatestEmail(String email, String fragmentKey) {
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(emailSender, org.mockito.Mockito.atLeastOnce())
        .send(eq(email), anyString(), body.capture());
    Pattern link = Pattern.compile("/#" + fragmentKey + "=([A-Za-z0-9_-]+)");
    String token = null;
    for (String candidate : body.getAllValues()) {
      Matcher matcher = link.matcher(candidate);
      if (matcher.find()) {
        token = matcher.group(1);
      }
    }
    assertThat(token).as("a %s link emailed to %s", fragmentKey, email).isNotNull();
    return token;
  }

  @Test
  void aForgottenPasswordCanBeResetOnceWithTheEmailedLink() throws Exception {
    String email = "reset.flow@example.com";
    register(email);

    postJson("/api/v1/auth/forgot-password", Map.of("email", email))
        .andExpect(status().isAccepted());
    String token = tokenFromLatestEmail(email, "reset-password");

    postJson("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD))
        .andExpect(status().isNoContent());

    login(email, NEW_PASSWORD).andExpect(status().isOk());
    login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());

    // The link is single-use.
    postJson("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", "Third@Pass789"))
        .andExpect(status().isBadRequest());
    login(email, NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void aPasswordResetSignsOutEveryExistingSession() throws Exception {
    String email = "reset.sessions@example.com";
    String registerResponse =
        postJson(
                "/api/v1/auth/register",
                Map.of("email", email, "password", OLD_PASSWORD, "fullName", "Recovery Test"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String refreshToken = objectMapper.readTree(registerResponse).get("refreshToken").asText();

    postJson("/api/v1/auth/forgot-password", Map.of("email", email));
    String token = tokenFromLatestEmail(email, "reset-password");
    postJson("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD))
        .andExpect(status().isNoContent());

    postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void forgotPasswordAnswersTheSameForAnUnknownAddressAndSendsNothing() throws Exception {
    String email = "nobody.here@example.com";

    postJson("/api/v1/auth/forgot-password", Map.of("email", email))
        .andExpect(status().isAccepted());

    verify(emailSender, never()).send(eq(email), anyString(), anyString());
  }

  @Test
  void aResetLinkCannotBeUsedToVerifyAndAnInventedTokenIsRejected() throws Exception {
    String email = "reset.crossuse@example.com";
    register(email);
    postJson("/api/v1/auth/forgot-password", Map.of("email", email));
    String resetToken = tokenFromLatestEmail(email, "reset-password");

    postJson("/api/v1/auth/verify-email", Map.of("token", resetToken))
        .andExpect(status().isBadRequest());
    postJson(
            "/api/v1/auth/reset-password",
            Map.of("token", "not-a-token", "newPassword", NEW_PASSWORD))
        .andExpect(status().isBadRequest());

    login(email, OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void registrationEmailsAVerificationLinkThatMarksTheAddressVerified() throws Exception {
    String email = "verify.flow@example.com";
    String accessToken = register(email);

    mockMvc
        .perform(get("/api/v1/profile").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailVerified").value(false));

    String token = tokenFromLatestEmail(email, "verify-email");
    postJson("/api/v1/auth/verify-email", Map.of("token", token)).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/profile").header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.emailVerified").value(true));

    postJson("/api/v1/auth/verify-email", Map.of("token", token))
        .andExpect(status().isBadRequest());
  }

  @Test
  void resendingVerificationRequiresASessionAndSendsAnotherLink() throws Exception {
    String email = "verify.resend@example.com";
    String accessToken = register(email);

    mockMvc.perform(post("/api/v1/auth/resend-verification")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/v1/auth/resend-verification")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isAccepted());

    verify(emailSender, org.mockito.Mockito.times(2))
        .send(eq(email), contains("Confirm"), anyString());
  }

  @Test
  void anAccountWithOutstandingLinksCanStillBeDeleted() throws Exception {
    String email = "delete.with.tokens@example.com";
    String accessToken = register(email);
    postJson("/api/v1/auth/forgot-password", Map.of("email", email));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/profile")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNoContent());
  }
}
