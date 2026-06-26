package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
