package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.entity.User;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  public void sendMilestoneNotification(User user, int streakDays,
      UUID triggeringActivityId) {
    log.info(
        "NOTIFICATION userId={} email={} type=STREAK_MILESTONE streakDays={} triggeringActivityId={}",
        user.getId(),
        user.getEmail(),
        streakDays,
        triggeringActivityId);
  }
}
