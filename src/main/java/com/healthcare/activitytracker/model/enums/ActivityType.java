package com.healthcare.activitytracker.model.enums;

public enum ActivityType {
  WALKING(3.5),
  RUNNING(9.8),
  YOGA(3.0),
  CYCLING(7.5),
  SWIMMING(8.0),
  STRENGTH_TRAINING(6.0),
  STRETCHING(2.5),
  OTHER(4.0);

  private final double metValue;

  ActivityType(double metValue) {
    this.metValue = metValue;
  }

  public double getMetValue() {
    return metValue;
  }
}
