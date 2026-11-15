package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.healthcare.activitytracker.config.MailProperties;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailSenderTest {

  /** Runs tasks on the calling thread, or rejects them all, so sends are deterministic. */
  private static ExecutorService executor(boolean rejecting) {
    return new AbstractExecutorService() {
      private boolean shutdown;

      @Override
      public void execute(Runnable command) {
        if (rejecting) {
          throw new RejectedExecutionException("queue full");
        }
        command.run();
      }

      @Override
      public void shutdown() {
        shutdown = true;
      }

      @Override
      public List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
      }

      @Override
      public boolean isShutdown() {
        return shutdown;
      }

      @Override
      public boolean isTerminated() {
        return shutdown;
      }

      @Override
      public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
      }
    };
  }

  private static MailProperties properties(String from) {
    MailProperties properties = new MailProperties();
    properties.setFrom(from);
    return properties;
  }

  @Test
  void smtpSender_sendsFromTheConfiguredAddress() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    SmtpEmailSender sender =
        new SmtpEmailSender(mailSender, properties("tracker@example.com"), executor(false));

    sender.send("user@example.com", "Subject", "Body");

    ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(message.capture());
    assertThat(message.getValue().getFrom()).isEqualTo("tracker@example.com");
    assertThat(message.getValue().getTo()).containsExactly("user@example.com");
    assertThat(message.getValue().getSubject()).isEqualTo("Subject");
    assertThat(message.getValue().getText()).isEqualTo("Body");
  }

  @Test
  void smtpSender_swallowsADeliveryFailure() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
    SmtpEmailSender sender =
        new SmtpEmailSender(mailSender, properties("tracker@example.com"), executor(false));

    assertThatCode(() -> sender.send("user@example.com", "Subject", "Body"))
        .doesNotThrowAnyException();
  }

  @Test
  void smtpSender_dropsTheMessageWhenTheQueueIsFull() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    SmtpEmailSender sender =
        new SmtpEmailSender(mailSender, properties("tracker@example.com"), executor(true));

    assertThatCode(() -> sender.send("user@example.com", "Subject", "Body"))
        .doesNotThrowAnyException();
    verifyNoInteractions(mailSender);
  }

  @Test
  void smtpSender_refusesToStartWithoutAFromAddress() {
    JavaMailSender mailSender = mock(JavaMailSender.class);

    assertThatThrownBy(() -> new SmtpEmailSender(mailSender, properties(" "), executor(false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MAIL_FROM");
  }

  @Test
  void smtpSender_stopsItsExecutorOnShutdown() {
    ExecutorService executor = executor(false);
    SmtpEmailSender sender =
        new SmtpEmailSender(mock(JavaMailSender.class), properties("a@example.com"), executor);

    sender.shutdown();

    assertThat(executor.isShutdown()).isTrue();
  }

  @Test
  void smtpSender_defaultConstructorBuildsAWorkingSender() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    SmtpEmailSender sender = new SmtpEmailSender(mailSender, properties("a@example.com"));

    sender.send("user@example.com", "Subject", "Body");
    sender.shutdown();

    verify(mailSender, org.mockito.Mockito.timeout(2000)).send(any(SimpleMailMessage.class));
  }

  @Test
  void disabledSender_sendsNothingAndDoesNotThrow() {
    assertThatCode(() -> new DisabledEmailSender().send("user@example.com", "Subject", "Body"))
        .doesNotThrowAnyException();
  }
}
