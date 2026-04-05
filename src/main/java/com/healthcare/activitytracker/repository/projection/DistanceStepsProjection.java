package com.healthcare.activitytracker.repository.projection;

/** Interface projection for total distance and steps aggregates. */
public interface DistanceStepsProjection {
  Double getTotalDistance();

  Long getTotalSteps();
}
