package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GoogleHealthConnectionRepository
    extends JpaRepository<GoogleHealthConnection, UUID> {

  Optional<GoogleHealthConnection> findByUserId(UUID userId);

  List<GoogleHealthConnection> findByStatus(ConnectionStatus status);
}
