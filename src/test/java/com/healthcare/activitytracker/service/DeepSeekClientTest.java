package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.DeepSeekProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeepSeekClientTest {

  @Mock private DeepSeekConnectionService connectionService;

  private HttpServer server;
  private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
  private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
  private volatile String responseBody =
      "{\"choices\":[{\"message\":{\"content\":\"Great week!\"}}]}";
  private volatile int responseStatus = 200;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          lastRequestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(responseStatus, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private DeepSeekProperties props() {
    DeepSeekProperties props = new DeepSeekProperties();
    props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
    props.setModel("deepseek-chat");
    props.setConnectTimeoutMs(1000);
    props.setReadTimeoutMs(2000);
    return props;
  }

  private DeepSeekClient client(DeepSeekProperties props) {
    return new DeepSeekClient(props, connectionService, new ObjectMapper());
  }

  private DeepSeekClient client() {
    return client(props());
  }

  private void stubKey(String decrypted) {
    when(connectionService.findDecryptedApiKey(userId)).thenReturn(Optional.of(decrypted));
  }

  @Test
  void generate_returnsResponseText_whenUserHasKey() {
    stubKey("sk-real-key");

    Optional<String> result = client().generate(userId, "Summarize my week");

    assertThat(result).hasValue("Great week!");
    assertThat(lastAuthHeader.get()).isEqualTo("Bearer sk-real-key");
  }

  @Test
  void generate_sendsModelAndMessages() {
    stubKey("sk-real-key");

    client().generate(userId, "Summarize my week");

    assertThat(lastRequestBody.get())
        .contains("\"model\":\"deepseek-chat\"")
        .contains("\"content\":\"Summarize my week\"")
        .contains("\"stream\":false");
  }

  @Test
  void generateJson_requestsJsonObjectFormat() {
    stubKey("sk-real-key");

    client().generateJson(userId, "Extract mood");

    assertThat(lastRequestBody.get()).contains("\"response_format\":{\"type\":\"json_object\"}");
  }

  @Test
  void generate_returnsEmpty_whenNoKeyOnFile() {
    when(connectionService.findDecryptedApiKey(userId)).thenReturn(Optional.empty());

    assertThat(client().generate(userId, "hi")).isEmpty();
    assertThat(lastRequestBody.get()).isNull();
  }

  @Test
  void generate_returnsEmpty_whenKeyLookupThrows() {
    // The lookup (DB fetch + decrypt) happens inside doGenerate's try/catch, so a corrupted or
    // unrotatable-key failure degrades the same way a network failure does, instead of
    // propagating as an uncaught exception (the bug this test guards against).
    when(connectionService.findDecryptedApiKey(userId))
        .thenThrow(new IllegalStateException("Token decryption failed"));

    assertThat(client().generate(userId, "hi")).isEmpty();
    assertThat(lastRequestBody.get()).isNull();
  }

  @Test
  void generate_returnsEmpty_whenDisabled() {
    // The enabled check short-circuits before the key lookup, so nothing is stubbed on
    // connectionService here — that lookup must never happen.
    DeepSeekProperties props = props();
    props.setEnabled(false);

    assertThat(client(props).generate(userId, "hi")).isEmpty();
    assertThat(lastRequestBody.get()).isNull();
  }

  @Test
  void generate_returnsEmpty_onServerError() {
    stubKey("sk-real-key");
    responseStatus = 500;

    assertThat(client().generate(userId, "hi")).isEmpty();
  }

  @Test
  void generate_returnsEmpty_onMalformedResponse() {
    stubKey("sk-real-key");
    responseBody = "not json at all";

    assertThat(client().generate(userId, "hi")).isEmpty();
  }

  @Test
  void generate_returnsEmpty_whenContentFieldMissing() {
    stubKey("sk-real-key");
    responseBody = "{\"choices\":[]}";

    assertThat(client().generate(userId, "hi")).isEmpty();
  }

  @Test
  void generate_returnsEmpty_whenUnreachable() {
    stubKey("sk-real-key");
    DeepSeekClient client = client();
    server.stop(0);

    assertThat(client.generate(userId, "hi")).isEmpty();
  }
}
