package com.healthcare.activitytracker.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(2)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtUtil jwtUtil;
  private final TokenBlacklistService tokenBlacklistService;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(
      JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService, ObjectMapper objectMapper) {
    this.jwtUtil = jwtUtil;
    this.tokenBlacklistService = tokenBlacklistService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String requestId = UUID.randomUUID().toString().substring(0, 8);
    MDC.put("requestId", requestId);

    String header = request.getHeader(AUTH_HEADER);

    if (header != null && header.startsWith(BEARER_PREFIX)) {
      String token = header.substring(BEARER_PREFIX.length());
      try {
        // Parse and verify the signature/expiry once, then derive every claim from the result.
        // parseAccessToken throws on an invalid signature (incl. refresh tokens signed with the
        // refresh key) or an expired token.
        Claims claims;
        try {
          claims = jwtUtil.parseAccessToken(token);
        } catch (Exception e) {
          log.warn("Invalid or expired JWT token for request {}", request.getRequestURI());
          sendUnauthorized(response, "Invalid or expired token");
          return;
        }
        // Verify the token type claim for defence-in-depth (separate keys already
        // prevent refresh tokens from validating as access tokens).
        if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))) {
          log.warn("Non-access token used for request {}", request.getRequestURI());
          sendUnauthorized(response, "Invalid token type");
          return;
        }
        // Reject explicitly revoked tokens (logout)
        if (tokenBlacklistService.isRevoked(token)) {
          log.warn("Revoked token used for request {}", request.getRequestURI());
          sendUnauthorized(response, "Token has been revoked");
          return;
        }
        UUID userId = UUID.fromString(claims.getSubject());
        // Reject tokens issued before the user's last "log out everywhere" (global invalidation).
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt != null
            && tokenBlacklistService.isUserInvalidatedSince(
                userId.toString(), issuedAt.getTime())) {
          log.warn(
              "Token issued before global logout used for request {}", request.getRequestURI());
          sendUnauthorized(response, "Token has been revoked");
          return;
        }
        MDC.put("userId", userId.toString());

        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception e) {
        log.warn("JWT authentication failed: {}", e.getMessage());
        sendUnauthorized(response, "Authentication failed");
        return;
      }
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }

  private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    objectMapper.writeValue(response.getOutputStream(), Map.of("status", 401, "error", message));
  }
}
