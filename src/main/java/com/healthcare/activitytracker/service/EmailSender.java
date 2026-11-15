package com.healthcare.activitytracker.service;

/**
 * Sends one plain-text email. Exactly one implementation is active, chosen by {@code
 * app.mail.enabled}: {@link SmtpEmailSender} when true, {@link DisabledEmailSender} otherwise.
 *
 * <p>Sending is best-effort and never throws to the caller: the flows that send mail (forgot
 * password, registration) must respond the same way whether or not delivery works, both so a mail
 * outage does not break registration and so the response does not reveal whether an address has an
 * account.
 */
public interface EmailSender {

  void send(String to, String subject, String body);
}
