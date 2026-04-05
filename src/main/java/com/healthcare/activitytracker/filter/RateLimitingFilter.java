package com.healthcare.activitytracker.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

  /**
   * Auth endpoints that receive stricter rate-limiting protection. Includes /refresh to prevent
   * replay attacks with stolen refresh tokens.
   */
  private static final Set<String> AUTH_RATE_LIMITED_URIS =
      Set.of("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh");

  @Value("${app.rate-limit.auth-requests-per-minute:10}")
  private int authRequestsPerMinute;

  /** General API rate limit — more generous than auth endpoints. */
  @Value("${app.rate-limit.api-requests-per-minute:60}")
  private int apiRequestsPerMinute;

  /**
   * Comma-separated list of IP addresses that are trusted reverse proxies. Only when the real
   * remote address is in this set will X-Forwarded-For be trusted. Leaving this empty means
   * X-Forwarded-For is never trusted (safest default).
   */
  @Value("${app.security.trusted-proxies:}")
  private String trustedProxiesConfig;

  private Set<String> trustedProxies;

  /**
   * Stores per-IP buckets together with the last-access timestamp (epoch ms). The timestamp is used
   * to evict entries that have been idle for more than two minutes, preventing unbounded memory
   * growth under varied-IP traffic. Separate maps for auth and general API traffic to enforce
   * independent limits.
   */
  private final ConcurrentHashMap<String, BucketEntry> authBuckets = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, BucketEntry> apiBuckets = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    trustedProxies =
        Arrays.stream(trustedProxiesConfig.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    log.info("Rate limiter initialized: trusted proxies={}", trustedProxies);
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String uri = request.getRequestURI();
    String clientIp = getClientIp(request);

    // Stricter limit for auth endpoints (brute force protection)
    if (AUTH_RATE_LIMITED_URIS.contains(uri)) {
      if (!tryConsume(authBuckets, clientIp, authRequestsPerMinute)) {
        sendRateLimitResponse(response, clientIp, uri);
        return;
      }
    }

    // General API rate limit for all /api/ endpoints
    if (uri.startsWith("/api/")) {
      if (!tryConsume(apiBuckets, clientIp, apiRequestsPerMinute)) {
        sendRateLimitResponse(response, clientIp, uri);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private boolean tryConsume(
      ConcurrentHashMap<String, BucketEntry> bucketMap, String clientIp, int limitPerMinute) {
    Bucket bucket = getOrCreateBucket(bucketMap, clientIp, limitPerMinute);
    return bucket.tryConsume(1);
  }

  private void sendRateLimitResponse(HttpServletResponse response, String clientIp, String uri)
      throws IOException {
    log.warn("Rate limit exceeded for IP {} on {}", clientIp, uri);
    response.setStatus(429);
    response.setContentType("application/json");
    response.setHeader("Retry-After", "60");
    response
        .getWriter()
        .write("{\"status\":429,\"error\":\"Too many requests. Please try again later.\"}");
  }

  private Bucket getOrCreateBucket(
      ConcurrentHashMap<String, BucketEntry> bucketMap, String ip, int limitPerMinute) {
    return bucketMap
        .compute(
            ip,
            (key, existing) -> {
              if (existing == null) {
                Bandwidth limit =
                    Bandwidth.classic(
                        limitPerMinute, Refill.intervally(limitPerMinute, Duration.ofMinutes(1)));
                return new BucketEntry(
                    Bucket.builder().addLimit(limit).build(), System.currentTimeMillis());
              }
              // Refresh last-access time on every use
              return new BucketEntry(existing.bucket(), System.currentTimeMillis());
            })
        .bucket();
  }

  /**
   * Evicts bucket entries that have been idle for more than 2 minutes. Runs every minute, keeping
   * memory bounded even under IP-cycling attacks.
   */
  @Scheduled(fixedRate = 60_000)
  public void evictIdleBuckets() {
    long threshold = System.currentTimeMillis() - 120_000;
    evictFrom(authBuckets, threshold);
    evictFrom(apiBuckets, threshold);
  }

  private void evictFrom(ConcurrentHashMap<String, BucketEntry> bucketMap, long threshold) {
    int before = bucketMap.size();
    bucketMap.entrySet().removeIf(e -> e.getValue().lastAccessMs() < threshold);
    int removed = before - bucketMap.size();
    if (removed > 0) {
      log.debug(
          "Rate limiter eviction: removed {} idle buckets, remaining={}",
          removed,
          bucketMap.size());
    }
  }

  /**
   * Resolves the real client IP. X-Forwarded-For is only trusted when the direct TCP peer is a
   * known proxy, preventing spoofing via a forged header from untrusted sources.
   */
  private String getClientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    if (!trustedProxies.isEmpty() && trustedProxies.contains(remoteAddr)) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
      }
    }
    return remoteAddr;
  }

  /** Pairs a rate-limit Bucket with the epoch-ms timestamp of its last use. */
  private record BucketEntry(Bucket bucket, long lastAccessMs) {}
}
