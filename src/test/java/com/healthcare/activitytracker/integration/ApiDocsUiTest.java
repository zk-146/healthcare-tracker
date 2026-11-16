package com.healthcare.activitytracker.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Swagger UI is served from the swagger-ui webjar, whose version pom.xml overrides. springdoc
 * builds the asset path from the version it is told about, so if the two disagree the page loads as
 * a 404 while everything else — including /v3/api-docs — keeps working. Nothing else notices.
 */
@SpringBootTest(
    properties = {
      // The test profile switches springdoc off for the @WebMvcTest slices; this needs it on.
      "springdoc.api-docs.enabled=true",
      "springdoc.swagger-ui.enabled=true",
      "app.outbox.enabled=false",
      "app.ollama.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.auto-create=false"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsUiTest {

  @Autowired MockMvc mockMvc;

  @Test
  void swaggerUiPageAndItsAssetsAreServed() throws Exception {
    mockMvc
        .perform(get("/swagger-ui.html"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/swagger-ui/index.html"));
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
  }

  @Test
  void openApiDocumentIsServed() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }
}
