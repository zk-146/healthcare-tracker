package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoopAiTextClientTest {

  private final NoopAiTextClient client = new NoopAiTextClient();

  @Test
  void generate_alwaysReturnsEmpty() {
    assertThat(client.generate(UUID.randomUUID(), "prompt")).isEmpty();
    assertThat(client.generate(null, "prompt")).isEmpty();
  }

  @Test
  void generateJson_alwaysReturnsEmpty() {
    assertThat(client.generateJson(UUID.randomUUID(), "prompt")).isEmpty();
  }
}
