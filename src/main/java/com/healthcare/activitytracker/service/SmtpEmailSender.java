package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.config.MailProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Delivers mail over SMTP ({@code spring.mail.*}). Active when {@code app.mail.enabled=true}.
 *
 * <p>Messages are handed to a small background pool rather than sent on the request thread. An SMTP
 * round trip takes long enough to measure, and forgot-password only sends when the address has an
 * account — sent inline, the response time alone would answer "is this address registered?", which
 * is exactly what that endpoint is built not to reveal.
 *
 * <p>The queue is bounded and overflow is dropped (and logged): under a flood it is better to lose
 * mail than to grow without limit.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
  private static final int QUEUE_CAPACITY = 200;

  private final JavaMailSender mailSender;
  private final String from;
  private final ExecutorService executor;

  @Autowired
  public SmtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
    this(mailSender, properties, defaultExecutor());
  }

  /** Seam for tests, which run sends on the calling thread. */
  SmtpEmailSender(JavaMailSender mailSender, MailProperties properties, ExecutorService executor) {
    if (properties.getFrom() == null || properties.getFrom().isBlank()) {
      throw new IllegalStateException(
          "MAIL_ENABLED is true but no From address is configured. Set MAIL_FROM.");
    }
    this.mailSender = mailSender;
    this.from = properties.getFrom();
    this.executor = executor;
    log.info("Outbound email enabled (SMTP)");
  }

  private static ExecutorService defaultExecutor() {
    return new ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(QUEUE_CAPACITY),
        runnable -> {
          Thread thread = new Thread(runnable, "mail-sender");
          thread.setDaemon(true);
          return thread;
        });
  }

  @Override
  public void send(String to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    try {
      executor.execute(() -> deliver(message, subject));
    } catch (RejectedExecutionException e) {
      log.error("Email dropped, send queue is full: subject=\"{}\"", subject);
    }
  }

  private void deliver(SimpleMailMessage message, String subject) {
    try {
      mailSender.send(message);
      log.info("Email sent: subject=\"{}\"", subject);
    } catch (Exception e) {
      // The recipient is deliberately not logged (PII policy: identifiers only).
      log.error("Email delivery failed: subject=\"{}\" cause={}", subject, e.getMessage());
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
