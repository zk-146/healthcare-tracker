package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.NotesInsightResponse;
import com.healthcare.activitytracker.model.entity.Activity;
import com.healthcare.activitytracker.repository.ActivityRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotesAnalysisServiceTest {

  @Mock private ActivityRepository activityRepository;
  @Mock private OllamaClient ollamaClient;

  private NotesAnalysisService notesAnalysisService;

  private final UUID userId = UUID.randomUUID();
  private final UUID activityId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    notesAnalysisService =
        new NotesAnalysisService(activityRepository, ollamaClient, new ObjectMapper());
  }

  private void stubActivityWithNotes(String notes) {
    Activity activity = Activity.builder().id(activityId).notes(notes).build();
    when(activityRepository.findByIdAndUserId(activityId, userId))
        .thenReturn(Optional.of(activity));
  }

  @Test
  void extractsMoodAndPain_fromModelJson() {
    stubActivityWithNotes("Felt great but my left knee ached near the end");
    when(ollamaClient.generateJson(any()))
        .thenReturn(
            Optional.of(
                "{\"mood\":\"positive\",\"painMentioned\":true,"
                    + "\"painDescription\":\"left knee ache\"}"));

    NotesInsightResponse result = notesAnalysisService.analyzeNotes(userId, activityId);

    assertThat(result.isAvailable()).isTrue();
    assertThat(result.getMood()).isEqualTo("positive");
    assertThat(result.getPainMentioned()).isTrue();
    assertThat(result.getPainDescription()).isEqualTo("left knee ache");
    assertThat(result.getActivityId()).isEqualTo(activityId);
  }

  @Test
  void unavailable_whenOllamaDown() {
    stubActivityWithNotes("some notes");
    when(ollamaClient.generateJson(any())).thenReturn(Optional.empty());

    NotesInsightResponse result = notesAnalysisService.analyzeNotes(userId, activityId);

    assertThat(result.isAvailable()).isFalse();
    assertThat(result.getMessage()).isEqualTo(NotesAnalysisService.UNAVAILABLE_MESSAGE);
  }

  @Test
  void unavailable_whenModelOutputMalformed() {
    stubActivityWithNotes("some notes");
    when(ollamaClient.generateJson(any())).thenReturn(Optional.of("not json {{{"));

    NotesInsightResponse result = notesAnalysisService.analyzeNotes(userId, activityId);

    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  void unavailable_whenModelReturnsNonObjectJson() {
    stubActivityWithNotes("some notes");
    when(ollamaClient.generateJson(any())).thenReturn(Optional.of("\"just a string\""));

    NotesInsightResponse result = notesAnalysisService.analyzeNotes(userId, activityId);

    assertThat(result.isAvailable()).isFalse();
  }

  @Test
  void clampsUnexpectedMood_toUnknown() {
    stubActivityWithNotes("some notes");
    when(ollamaClient.generateJson(any()))
        .thenReturn(Optional.of("{\"mood\":\"ecstatic\",\"painMentioned\":false}"));

    NotesInsightResponse result = notesAnalysisService.analyzeNotes(userId, activityId);

    assertThat(result.isAvailable()).isTrue();
    assertThat(result.getMood()).isEqualTo("unknown");
  }

  @Test
  void truncatesOverlongPainDescription() {
    stubActivityWithNotes("some notes");
    when(ollamaClient.generateJson(any()))
        .thenReturn(
            Optional.of(
                "{\"mood\":\"neutral\",\"painMentioned\":true,\"painDescription\":\""
                    + "x".repeat(500)
                    + "\"}"));

    NotesInsightResponse result = notesAnalysisService.analyzeNotes(userId, activityId);

    assertThat(result.getPainDescription()).hasSize(200);
  }

  @Test
  void noAnalysis_whenActivityHasNoNotes() {
    stubActivityWithNotes(null);

    NotesInsightResponse result = notesAnalysisService.analyzeNotes(userId, activityId);

    assertThat(result.isAvailable()).isFalse();
    assertThat(result.getMessage()).isEqualTo(NotesAnalysisService.NO_NOTES_MESSAGE);
    verifyNoInteractions(ollamaClient);
  }

  @Test
  void throwsNotFound_whenActivityMissing() {
    when(activityRepository.findByIdAndUserId(activityId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> notesAnalysisService.analyzeNotes(userId, activityId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
