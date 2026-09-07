package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MilestoneMessageServiceTest {

  @Mock private OllamaClient ollamaClient;

  private MilestoneMessageService messageService;

  @BeforeEach
  void setUp() {
    messageService = new MilestoneMessageService(ollamaClient);
  }

  @Test
  void returnsModelCopy_whenAvailable() {
    when(ollamaClient.generate(any())).thenReturn(Optional.of("Seven days strong — amazing!"));
    assertThat(messageService.milestoneMessage(7)).isEqualTo("Seven days strong — amazing!");
  }

  @Test
  void fallsBackToTemplate_whenOllamaUnavailable() {
    when(ollamaClient.generate(any())).thenReturn(Optional.empty());
    String message = messageService.milestoneMessage(7);
    assertThat(message).contains("7").contains("streak");
  }

  @Test
  void promptMentionsStreak_andForbidsPersonalNames() {
    when(ollamaClient.generate(any())).thenReturn(Optional.of("ok"));
    messageService.milestoneMessage(30);

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(ollamaClient).generate(prompt.capture());
    assertThat(prompt.getValue()).contains("30-day").contains("Do not use a personal name");
  }
}
