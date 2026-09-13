package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MilestoneMessageServiceTest {

  @Mock private AiTextClient aiTextClient;

  private MilestoneMessageService messageService;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    messageService = new MilestoneMessageService(aiTextClient);
  }

  @Test
  void returnsModelCopy_whenAvailable() {
    when(aiTextClient.generate(eq(userId), any()))
        .thenReturn(Optional.of("Seven days strong — amazing!"));
    assertThat(messageService.milestoneMessage(userId, 7))
        .isEqualTo("Seven days strong — amazing!");
  }

  @Test
  void fallsBackToTemplate_whenAiClientUnavailable() {
    when(aiTextClient.generate(eq(userId), any())).thenReturn(Optional.empty());
    String message = messageService.milestoneMessage(userId, 7);
    assertThat(message).contains("7").contains("streak");
  }

  @Test
  void promptMentionsStreak_andForbidsPersonalNames() {
    when(aiTextClient.generate(eq(userId), any())).thenReturn(Optional.of("ok"));
    messageService.milestoneMessage(userId, 30);

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(aiTextClient).generate(eq(userId), prompt.capture());
    assertThat(prompt.getValue()).contains("30-day").contains("Do not use a personal name");
  }
}
