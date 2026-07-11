package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ActivityType;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Estimates calories burned for an activity using the standard MET formula, as a server-side
 * fallback when the client did not supply {@code caloriesBurned}.
 *
 * <p>Formula: {@code kcal = minutes × MET × 3.5 × weightKg / 200} (ACSM metabolic equation), scaled
 * by an age factor approximating the ~3%-per-decade decline in resting metabolic rate after age 25
 * (floored at 0.85). Pure computation — no AI, no I/O.
 */
@Component
public class CalorieEstimator {

  /** Used when the user has not recorded a body weight (average adult weight). */
  static final double DEFAULT_WEIGHT_KG = 70.0;

  private static final double MIN_AGE_FACTOR = 0.85;
  private static final double AGE_FACTOR_PER_YEAR = 0.003;
  private static final int AGE_FACTOR_BASELINE_YEARS = 25;

  /**
   * Typical speeds (km/h) used to derive a duration when only distance was recorded. Types not
   * listed cannot be estimated from distance alone.
   */
  private static final Map<ActivityType, Double> TYPICAL_SPEED_KMH =
      Map.of(
          ActivityType.WALKING, 5.0,
          ActivityType.RUNNING, 10.0,
          ActivityType.CYCLING, 18.0,
          ActivityType.SWIMMING, 3.0);

  /**
   * Estimates calories burned, preferring recorded duration and falling back to a distance-derived
   * duration. Empty when neither is available or the type is null.
   */
  public Optional<Double> estimateCalories(
      ActivityType type, Integer durationMinutes, Double distanceKm, User user) {
    if (type == null) {
      return Optional.empty();
    }
    Double minutes = resolveMinutes(type, durationMinutes, distanceKm);
    if (minutes == null) {
      return Optional.empty();
    }
    double weightKg =
        user != null && user.getWeightKg() != null ? user.getWeightKg() : DEFAULT_WEIGHT_KG;
    double calories = minutes * type.getMetValue() * 3.5 * weightKg / 200.0;
    calories *= ageFactor(user);
    return Optional.of(Math.round(calories * 10.0) / 10.0);
  }

  private static Double resolveMinutes(
      ActivityType type, Integer durationMinutes, Double distanceKm) {
    if (durationMinutes != null && durationMinutes > 0) {
      return durationMinutes.doubleValue();
    }
    Double speedKmh = TYPICAL_SPEED_KMH.get(type);
    if (distanceKm != null && distanceKm > 0 && speedKmh != null) {
      return distanceKm / speedKmh * 60.0;
    }
    return null;
  }

  private static double ageFactor(User user) {
    if (user == null || user.getDateOfBirth() == null) {
      return 1.0;
    }
    int age = Period.between(user.getDateOfBirth(), LocalDate.now()).getYears();
    if (age <= AGE_FACTOR_BASELINE_YEARS) {
      return 1.0;
    }
    return Math.max(MIN_AGE_FACTOR, 1.0 - AGE_FACTOR_PER_YEAR * (age - AGE_FACTOR_BASELINE_YEARS));
  }
}
