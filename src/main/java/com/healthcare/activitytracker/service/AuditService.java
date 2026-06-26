package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.entity.AuditLog;
import com.healthcare.activitytracker.model.enums.AuditEventType;
import com.healthcare.activitytracker.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes compliance audit events to the {@code audit_log} table and to a dedicated {@code AUDIT}
 * logger.
 *
 * <p>Each record is written in its own {@code REQUIRES_NEW} transaction so the audit trail survives
 * even when the surrounding business transaction rolls back. Detail strings must never contain PHI
 * or credentials — pass identifiers and event metadata only.
 */
@Service
public class AuditService {

  private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

  private final AuditLogRepository auditLogRepository;

  public AuditService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID userId, AuditEventType eventType, String detail) {
    String ip = currentClientIp();
    AuditLog entry =
        AuditLog.builder().userId(userId).eventType(eventType).ipAddress(ip).detail(detail).build();
    auditLogRepository.save(entry);
    auditLog.info("event={} userId={} ip={} detail={}", eventType, userId, ip, detail);
  }

  public void record(UUID userId, AuditEventType eventType) {
    record(userId, eventType, null);
  }

  /**
   * Best-effort client IP from the current request, honouring a single {@code X-Forwarded-For} hop.
   * Returns null outside a request context (e.g. scheduled jobs).
   */
  private String currentClientIp() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      HttpServletRequest request = attributes.getRequest();
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].strip();
      }
      return request.getRemoteAddr();
    }
    return null;
  }
}
