package com.healthcare.activitytracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for the account emails the application sends (password reset, email verification).
 *
 * <p>Bound from {@code app.mail.*}. The SMTP connection itself is Spring's standard {@code
 * spring.mail.*}; this only covers what the messages say and how long their links live. Delivery is
 * off by default so the application and its tests run with no mail server.
 */
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

  /** Master switch. When false no mail is sent; see {@code DisabledEmailSender}. */
  private boolean enabled = false;

  /** The From address. Must be one the SMTP provider is allowed to send as. */
  private String from = "no-reply@localhost";

  /**
   * The externally reachable origin of this deployment, e.g. {@code https://tracker.example.com}.
   * Links in emails are built on it, so a wrong value here means links that lead nowhere.
   */
  private String publicBaseUrl = "http://localhost:8080";

  /** How long a password-reset link works. Short: the link is as good as the password. */
  private int resetTokenMinutes = 30;

  /** How long an email-verification link works. */
  private int verificationTokenHours = 24;

  /**
   * Minimum gap between two emails of the same kind to one account. Without it, an unauthenticated
   * caller could use forgot-password to flood somebody else's inbox.
   */
  private int resendCooldownSeconds = 60;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(String from) {
    this.from = from;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public int getResetTokenMinutes() {
    return resetTokenMinutes;
  }

  public void setResetTokenMinutes(int resetTokenMinutes) {
    this.resetTokenMinutes = resetTokenMinutes;
  }

  public int getVerificationTokenHours() {
    return verificationTokenHours;
  }

  public void setVerificationTokenHours(int verificationTokenHours) {
    this.verificationTokenHours = verificationTokenHours;
  }

  public int getResendCooldownSeconds() {
    return resendCooldownSeconds;
  }

  public void setResendCooldownSeconds(int resendCooldownSeconds) {
    this.resendCooldownSeconds = resendCooldownSeconds;
  }
}
