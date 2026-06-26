package com.healthcare.activitytracker.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that boot the full Spring context against a real PostgreSQL
 * instance with Flyway migrations applied. This exercises the production schema and dialect that
 * the H2-based unit tests cannot.
 *
 * <p>The container is shared across all subclasses (static, started once) and skipped automatically
 * when Docker is unavailable ({@code disabledWithoutDocker}), so the build still passes on machines
 * without a Docker daemon while running for real in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
