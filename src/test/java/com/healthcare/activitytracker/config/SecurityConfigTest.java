package com.healthcare.activitytracker.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.activitytracker.controller.AuthController;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SecurityConfigTest {

  @Autowired MockMvc mockMvc;

  @MockBean AuthService authService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  @Test
  void cspAllowsSameOriginAssetsAndApiCalls() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/login"))
        .andExpect(
            header()
                .string("Content-Security-Policy", Matchers.containsString("default-src 'self'")))
        .andExpect(
            header()
                .string("Content-Security-Policy", Matchers.containsString("connect-src 'self'")));
  }

  @Test
  void cspNeverPermitsInlineScripts() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/login"))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    Matchers.not(Matchers.containsString("unsafe-inline"))));
  }

  @Test
  void cspStillForbidsFraming() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/login"))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy", Matchers.containsString("frame-ancestors 'none'")));
  }

  /**
   * The SPA shell must be reachable without a token. In @WebMvcTest no static resource exists, so
   * the assertion is that the request is not rejected as unauthorised — 404 proves the permitAll
   * matcher applied, 401 would prove it did not.
   */
  @Test
  void staticShellIsNotRejectedAsUnauthorized() throws Exception {
    mockMvc.perform(get("/index.html")).andExpect(status().isNotFound());
    mockMvc.perform(get("/assets/app.js")).andExpect(status().isNotFound());
  }

  @Test
  void apiStillRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/summary/daily")).andExpect(status().isUnauthorized());
  }
}
