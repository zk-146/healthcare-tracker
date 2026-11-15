package com.healthcare.activitytracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole application on two real ports — the application port and a separate management
 * port — because where a request arrives is the access control for metrics, and that cannot be
 * shown with MockMvc.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "management.server.port=0",
      // No Kafka broker or Ollama in the test environment; neither is needed here.
      "app.outbox.enabled=false",
      "app.ollama.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      // Otherwise startup blocks for ~30s while KafkaAdmin waits on a broker that is not there.
      "spring.kafka.admin.auto-create=false"
    })
@ActiveProfiles("test")
// Boot switches metric exporters off in tests unless asked; without this the endpoint is a 404.
@AutoConfigureObservability
class ManagementPortSecurityTest {

  @LocalServerPort int serverPort;
  @LocalManagementPort int managementPort;

  @Autowired TestRestTemplate rest;

  private ResponseEntity<String> get(int port, String path) {
    return rest.getForEntity("http://localhost:" + port + path, String.class);
  }

  @Test
  void metricsAreServedOnTheManagementPort() {
    ResponseEntity<String> response = get(managementPort, "/actuator/prometheus");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("jvm_memory_used_bytes");
  }

  @Test
  void metricsAreNotServedOnTheApplicationPort() {
    assertThat(get(serverPort, "/actuator/prometheus").getStatusCode().is2xxSuccessful()).isFalse();
  }

  @Test
  void otherActuatorEndpointsStayClosedOnTheManagementPort() {
    assertThat(get(managementPort, "/actuator/env").getStatusCode().is2xxSuccessful()).isFalse();
  }

  @Test
  void readinessProbeIsPublicAndReportsUp() {
    ResponseEntity<String> response = get(managementPort, "/actuator/health/readiness");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void apiIsNotServedOnTheManagementPort() {
    // The management context only hosts actuator endpoints; the API is not mounted there.
    assertThat(get(managementPort, "/api/v1/summary/daily").getStatusCode().is2xxSuccessful())
        .isFalse();
  }
}
