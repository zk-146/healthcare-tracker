package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.entity.User;

/**
 * Transactional-email transport for account flows. The default implementation is a logging stub;
 * swap in an SMTP- or provider-backed implementation without touching the calling services.
 *
 * <p>Implementations receive the raw (unhashed) token because it must reach the user out of band.
 * They must never persist or log the raw token.
 */
public interface EmailService {

  /** Sends an email-address verification link/token to the user. */
  void sendVerificationEmail(User user, String rawToken);

  /** Sends a password-reset link/token to the user. */
  void sendPasswordResetEmail(User user, String rawToken);
}
