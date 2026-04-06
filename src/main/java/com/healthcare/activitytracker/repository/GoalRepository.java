package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.Goal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

  List<Goal> findByUserIdAndActiveTrue(UUID userId);

  Optional<Goal> findByIdAndUserId(UUID id, UUID userId);
}
