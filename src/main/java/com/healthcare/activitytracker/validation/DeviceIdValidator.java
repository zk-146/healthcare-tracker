package com.healthcare.activitytracker.validation;

import com.healthcare.activitytracker.model.dto.ActivityRequest;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DeviceIdValidator implements ConstraintValidator<ValidDeviceId, ActivityRequest> {

  @Override
  public boolean isValid(ActivityRequest request, ConstraintValidatorContext context) {
    if (request.getSource() == ActivitySource.IOT) {
      return request.getDeviceId() != null && !request.getDeviceId().isBlank();
    }
    return true;
  }
}
