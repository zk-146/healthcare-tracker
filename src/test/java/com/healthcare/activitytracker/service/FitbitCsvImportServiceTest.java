package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.exception.CsvImportException;
import com.healthcare.activitytracker.model.dto.CsvImportResponse;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.integration.ImportedWorkout;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class FitbitCsvImportServiceTest {

  private static final String HEADER =
      "Id,ActivityDate,TotalSteps,TotalDistance,TrackerDistance,LoggedActivitiesDistance,"
          + "VeryActiveDistance,ModeratelyActiveDistance,LightActiveDistance,"
          + "SedentaryActiveDistance,VeryActiveMinutes,FairlyActiveMinutes,LightlyActiveMinutes,"
          + "SedentaryMinutes,Calories\n";

  @Mock private ActivityService activityService;

  private FitbitCsvImportService fitbitCsvImportService;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    fitbitCsvImportService = new FitbitCsvImportService(activityService);
  }

  private MockMultipartFile csvFile(String content) {
    return new MockMultipartFile(
        "file", "dailyActivity_merged.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void importDailyActivity_importsValidRows() {
    String csv =
        HEADER + "1503960366,4/12/2016,13162,8.5,8.5,0,1.88,0.55,6.06,0,25,13,328,728,1985\n";
    when(activityService.importWorkout(
            eq(userId), any(), eq("fitbit-csv-import"), eq(ActivitySource.CSV_IMPORT)))
        .thenReturn(true);

    CsvImportResponse response = fitbitCsvImportService.importDailyActivity(userId, csvFile(csv));

    assertThat(response.getTotalRows()).isEqualTo(1);
    assertThat(response.getImported()).isEqualTo(1);
    assertThat(response.getDuplicatesSkipped()).isZero();
    assertThat(response.getFailed()).isZero();
    assertThat(response.getErrors()).isEmpty();

    verify(activityService)
        .importWorkout(
            eq(userId),
            org.mockito.ArgumentMatchers.argThat(
                (ImportedWorkout w) ->
                    "fitbit-csv:1503960366:4/12/2016".equals(w.getExternalId())
                        && w.getSteps() == 13162
                        && w.getDurationMinutes() == 25 + 13 + 328
                        && w.getCaloriesBurned() == 1985.0),
            eq("fitbit-csv-import"),
            eq(ActivitySource.CSV_IMPORT));
  }

  @Test
  void importDailyActivity_countsDuplicatesSeparatelyFromImports() {
    String csv =
        HEADER + "1503960366,4/12/2016,13162,8.5,8.5,0,1.88,0.55,6.06,0,25,13,328,728,1985\n";
    when(activityService.importWorkout(
            eq(userId), any(), eq("fitbit-csv-import"), eq(ActivitySource.CSV_IMPORT)))
        .thenReturn(false);

    CsvImportResponse response = fitbitCsvImportService.importDailyActivity(userId, csvFile(csv));

    assertThat(response.getImported()).isZero();
    assertThat(response.getDuplicatesSkipped()).isEqualTo(1);
  }

  @Test
  void importDailyActivity_reportsRowErrorsWithoutFailingWholeImport() {
    String csv =
        HEADER
            + "1503960366,4/12/2016,13162,8.5,8.5,0,1.88,0.55,6.06,0,25,13,328,728,1985\n"
            + "1503960366,not-a-date,100,1.0,1.0,0,0,0,1.0,0,0,0,10,0,50\n";
    when(activityService.importWorkout(
            eq(userId), any(), eq("fitbit-csv-import"), eq(ActivitySource.CSV_IMPORT)))
        .thenReturn(true);

    CsvImportResponse response = fitbitCsvImportService.importDailyActivity(userId, csvFile(csv));

    assertThat(response.getTotalRows()).isEqualTo(2);
    assertThat(response.getImported()).isEqualTo(1);
    assertThat(response.getFailed()).isEqualTo(1);
    assertThat(response.getErrors()).hasSize(1);
    assertThat(response.getErrors().get(0)).contains("Row 2");
  }

  @Test
  void importDailyActivity_rejectsEmptyFile() {
    MockMultipartFile empty = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

    assertThatThrownBy(() -> fitbitCsvImportService.importDailyActivity(userId, empty))
        .isInstanceOf(CsvImportException.class);
  }

  @Test
  void importDailyActivity_rejectsFileMissingRequiredColumns() {
    MockMultipartFile file = csvFile("Id,ActivityDate,TotalSteps\n1503960366,4/12/2016,13162\n");

    assertThatThrownBy(() -> fitbitCsvImportService.importDailyActivity(userId, file))
        .isInstanceOf(CsvImportException.class)
        .hasMessageContaining("Missing required column");
  }
}
