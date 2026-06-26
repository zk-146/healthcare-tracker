package com.healthcare.activitytracker.model.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full portable copy of a user's data (GDPR data-portability / "download my data"). Bundles the
 * profile, every activity, and every streak milestone for the requesting user.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDataExportResponse {
  private Instant exportedAt;
  private ProfileResponse profile;
  private List<ActivityResponse> activities;
  private List<StreakMilestoneResponse> streakMilestones;
}
