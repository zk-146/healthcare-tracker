package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.OllamaProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaClientTest {

  private HttpServer server;
  private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
  private volatile String responseBody = "{\"response\": \"Great week!\"}";
  private volatile int responseStatus = 200;
  private volatile long responseDelayMs = 0;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/api/generate",
        exchange -> {
          lastRequestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          try {
            Thread.sleep(responseDelayMs);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
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

  private OllamaProperties props() {
    OllamaProperties props = new OllamaProperties();
    props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
    props.setModel("qwen2.5:14b");
    props.setConnectTimeoutMs(1000);
    props.setReadTimeoutMs(2000);
    return props;
  }

  private OllamaClient client() {
    return new OllamaClient(props(), new ObjectMapper());
  }

  @Test
  void generate_returnsResponseText() {
    Optional<String> result = client().generate("Summarize my week");
    assertThat(result).hasValue("Great week!");
  }

  @Test
  void generate_sendsModelPromptAndNoStream() {
    client().generate("Summarize my week");
    assertThat(lastRequestBody.get())
        .contains("\"model\":\"qwen2.5:14b\"")
        .contains("\"prompt\":\"Summarize my week\"")
        .contains("\"stream\":false");
  }

  @Test
  void generateJson_requestsJsonFormat() {
    client().generateJson("Extract mood");
    assertThat(lastRequestBody.get()).contains("\"format\":\"json\"");
  }

  @Test
  void generateJson_isDeterministic() {
    client().generateJson("Extract mood");
    assertThat(lastRequestBody.get()).contains("\"temperature\":0.0").contains("\"seed\":42");
  }

  @Test
  void generate_usesConfiguredTemperature_withoutSeed() {
    OllamaProperties props = props();
    props.setTemperature(0.7);
    new OllamaClient(props, new ObjectMapper()).generate("Summarize my week");
    assertThat(lastRequestBody.get()).contains("\"temperature\":0.7").doesNotContain("\"seed\"");
  }

  @Test
  void generate_returnsEmpty_whenDisabled() {
    OllamaProperties props = props();
    props.setEnabled(false);
    OllamaClient disabled = new OllamaClient(props, new ObjectMapper());
    assertThat(disabled.generate("hi")).isEmpty();
    assertThat(lastRequestBody.get()).isNull();
  }

  @Test
  void generate_returnsEmpty_onServerError() {
    responseStatus = 500;
    assertThat(client().generate("hi")).isEmpty();
  }

  @Test
  void generate_returnsEmpty_onMalformedResponse() {
    responseBody = "not json at all";
    assertThat(client().generate("hi")).isEmpty();
  }

  @Test
  void generate_returnsEmpty_whenResponseFieldMissing() {
    responseBody = "{\"done\": true}";
    assertThat(client().generate("hi")).isEmpty();
  }

  @Test
  void generate_returnsEmpty_whenUnreachable() {
    OllamaClient client = client();
    server.stop(0);
    assertThat(client.generate("hi")).isEmpty();
  }

  @Test
  void generate_returnsEmpty_whenReadTimesOut() {
    responseDelayMs = 1000;
    OllamaProperties props = props();
    props.setReadTimeoutMs(150);
    OllamaClient client = new OllamaClient(props, new ObjectMapper());
    assertThat(client.generate("hi")).isEmpty();
  }
}
