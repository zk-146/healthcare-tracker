package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default {@link EmailService} implementation that records only that a message was queued. It does
 * not log the raw token (a credential) or the recipient address (PII) — the email is shown as a
 * masked address. Replace with a real SMTP/provider implementation in production.
 */
@Service
public class LoggingEmailService implements EmailService {

  private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

  @Override
  public void sendVerificationEmail(User user, String rawToken) {
    log.info(
        "Queued verification email userId={} to={} (stub transport — no message sent)",
        user.getId(),
        mask(user.getEmail()));
  }

  @Override
  public void sendPasswordResetEmail(User user, String rawToken) {
    log.info(
        "Queued password-reset email userId={} to={} (stub transport — no message sent)",
        user.getId(),
        mask(user.getEmail()));
  }

  /** Masks an email to "a***@domain" so logs carry no full PII. */
  private String mask(String email) {
    if (email == null) {
      return "unknown";
    }
    int at = email.indexOf('@');
    if (at <= 1) {
      return "***" + (at >= 0 ? email.substring(at) : "");
    }
    return email.charAt(0) + "***" + email.substring(at);
  }
}
