package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Exercises the in-memory fallback path (no Redis), which is what tests and dev runs use. */
class LoginAttemptServiceTest {

  private static final String EMAIL = "user@example.com";

  private static LoginAttemptService service(int maxAttempts, int lockoutMinutes) {
    return new LoginAttemptService(null, maxAttempts, lockoutMinutes, System::currentTimeMillis);
  }

  private static LoginAttemptService service(
      int maxAttempts, int lockoutMinutes, AtomicLong clock) {
    return new LoginAttemptService(null, maxAttempts, lockoutMinutes, clock::get);
  }

  private static void fail(LoginAttemptService service, String email, int times) {
    for (int i = 0; i < times; i++) {
      service.recordFailure(email);
    }
  }

  @Test
  void isNotLockedOutBeforeAnyFailure() {
    assertThat(service(3, 15).isLockedOut(EMAIL)).isFalse();
  }

  @Test
  void doesNotLockOutBelowTheThreshold() {
    LoginAttemptService service = service(3, 15);
    fail(service, EMAIL, 2);

    assertThat(service.isLockedOut(EMAIL)).isFalse();
  }

  @Test
  void locksOutOnceTheThresholdIsReached() {
    LoginAttemptService service = service(3, 15);
    fail(service, EMAIL, 3);

    assertThat(service.isLockedOut(EMAIL)).isTrue();
  }

  @Test
  void lockoutAppliesOnlyToTheCountedAccount() {
    LoginAttemptService service = service(3, 15);
    fail(service, EMAIL, 3);

    assertThat(service.isLockedOut("someone-else@example.com")).isFalse();
  }

  @Test
  void clearFailuresUnlocksTheAccount() {
    LoginAttemptService service = service(3, 15);
    fail(service, EMAIL, 3);
    assertThat(service.isLockedOut(EMAIL)).isTrue();

    service.clearFailures(EMAIL);

    assertThat(service.isLockedOut(EMAIL)).isFalse();
  }

  @Test
  void countsCaseAndWhitespaceVariantsAsTheSameAccount() {
    LoginAttemptService service = service(3, 15);
    service.recordFailure("  User@Example.com ");
    service.recordFailure("USER@EXAMPLE.COM");
    service.recordFailure(EMAIL);

    // Otherwise an attacker could reset the counter just by changing the capitalisation.
    assertThat(service.isLockedOut(EMAIL)).isTrue();
  }

  @Test
  void lockoutLiftsOnceTheWindowElapses() {
    AtomicLong clock = new AtomicLong(1_000_000L);
    LoginAttemptService service = service(3, 15, clock);
    fail(service, EMAIL, 3);
    assertThat(service.isLockedOut(EMAIL)).isTrue();

    clock.addAndGet(Duration.ofMinutes(15).toMillis() + 1);

    // The lockout is a cooldown, not a permanent ban: a legitimate user who mistyped their
    // password three times gets back in without an administrator.
    assertThat(service.isLockedOut(EMAIL)).isFalse();
  }

  @Test
  void failuresSeparatedByMoreThanTheWindowDoNotAccumulate() {
    AtomicLong clock = new AtomicLong(1_000_000L);
    LoginAttemptService service = service(3, 15, clock);

    for (int i = 0; i < 5; i++) {
      service.recordFailure(EMAIL);
      clock.addAndGet(Duration.ofMinutes(15).toMillis() + 1);
    }

    // Occasional typos spread over hours must never add up to a lockout.
    assertThat(service.isLockedOut(EMAIL)).isFalse();
  }

  @Test
  void evictionDropsWindowsThatHaveElapsed() {
    AtomicLong clock = new AtomicLong(1_000_000L);
    LoginAttemptService service = service(3, 15, clock);
    fail(service, EMAIL, 3);

    clock.addAndGet(Duration.ofMinutes(15).toMillis() + 1);
    service.evictElapsedWindows();

    assertThat(service.isLockedOut(EMAIL)).isFalse();
  }

  @Test
  void evictionLeavesLiveWindowsAlone() {
    LoginAttemptService service = service(3, 15);
    fail(service, EMAIL, 3);

    service.evictElapsedWindows();

    assertThat(service.isLockedOut(EMAIL)).isTrue();
  }
}
