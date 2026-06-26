package com.healthcare.activitytracker.integration;

import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.service.EmailService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test {@link EmailService} that records the raw (unhashed) tokens it is asked to deliver, keyed by
 * recipient email. Integration tests use this to recover the token they need to complete a flow
 * (the production code only ever persists the token's hash).
 */
public class RecordingEmailService implements EmailService {

  private final Map<String, String> verificationTokens = new ConcurrentHashMap<>();
  private final Map<String, String> resetTokens = new ConcurrentHashMap<>();

  @Override
  public void sendVerificationEmail(User user, String rawToken) {
    verificationTokens.put(user.getEmail(), rawToken);
  }

  @Override
  public void sendPasswordResetEmail(User user, String rawToken) {
    resetTokens.put(user.getEmail(), rawToken);
  }

  public String verificationTokenFor(String email) {
    return verificationTokens.get(email);
  }

  public String resetTokenFor(String email) {
    return resetTokens.get(email);
  }
}
