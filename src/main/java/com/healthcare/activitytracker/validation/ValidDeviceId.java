package com.healthcare.activitytracker.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = DeviceIdValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDeviceId {
  String message() default "deviceId is required when source is IOT";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
