package com.healthcare.activitytracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The {@link EmailSender} used when {@code app.mail.enabled} is not true. Sends nothing.
 *
 * <p>It records that a message was dropped, but never its recipient or body: the body of a reset
 * email is a working credential, and addresses are kept out of the logs everywhere else too.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.mail",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class DisabledEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(DisabledEmailSender.class);

  public DisabledEmailSender() {
    log.warn(
        "Outbound email is disabled (MAIL_ENABLED is not true). Password-reset and "
            + "email-verification messages will not be delivered, so a user who forgets their "
            + "password cannot recover the account. Configure SMTP and set MAIL_ENABLED=true.");
  }

  @Override
  public void send(String to, String subject, String body) {
    log.warn("Email not sent because outbound email is disabled: subject=\"{}\"", subject);
  }
}
