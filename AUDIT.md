# Codebase Audit — Healthcare Activity Tracker

**Date:** 2026-07-02
**Scope:** Full repository — application code, configuration, database schema, Docker, CI/CD, tests.
**Baseline verified:** `mvn test` passes — 81/81 tests green.

## Executive summary

This is a well-structured Spring Boot 3.2 / Java 17 service with a clearly layered
architecture (controller → service → repository), a hardened JWT implementation
(separate access/refresh signing keys, token rotation, hashed refresh-token storage,
revocation blacklist), Flyway-managed schema, optimistic locking, DB-level aggregation,
a non-root Docker image, and a CI pipeline with OWASP dependency-check, CodeQL, and
Trivy. Test coverage is broad (81 tests across controllers, services, filters,
validators, repositories).

The findings below are ordered by severity. There are **no critical
vulnerabilities**, but there are two functional bugs (CORS header, Kafka DLQ), one
HIPAA-posture logging issue, and a cluster of subtle servlet-filter wiring problems
worth fixing.

---

## High

### H1. CORS blocks the `X-User-Timezone` header for browser clients
`SecurityConfig.corsConfigurationSource()` allows only `Authorization` and
`Content-Type` request headers, but every `/api/v1/summary*` endpoint reads a custom
`X-User-Timezone` header (`SummaryController`). A browser preflight for a request
carrying that header will be rejected, so web clients silently lose timezone-aware
streaks (everything falls back to UTC) or fail the request outright.
**Fix:** add `X-User-Timezone` to `setAllowedHeaders`.

### H2. Kafka dead-letter topic is misconfigured — DLQ messages go elsewhere
`KafkaConfig` declares a topic `activity-events.DLQ` (3 partitions), but
`DeadLetterPublishingRecoverer` publishes to `<topic>.DLT` by default. The `.DLQ`
topic is never used. In environments with topic auto-creation, `.DLT` gets created
with 1 partition while the recoverer targets the *same partition* as the failed
record (0–2) — publishing a record from partition 1 or 2 to a 1-partition `.DLT`
fails, and the poison message is effectively lost after retries. In environments
without auto-creation, DLQ publishing always fails.
**Fix:** either name the bean topic `activity-events.DLT`, or pass a destination
resolver to the recoverer that targets `.DLQ` (and partition 0 or same-partition with
matching partition counts).

### H3. PII (email address) written to application logs
`NotificationService.sendMilestoneNotification` logs `email={}` alongside the user ID.
`RequestLoggingFilter` explicitly documents that sensitive data is excluded from logs
"to comply with HIPAA" — this log line contradicts that policy, and log pipelines
are rarely protected to the same standard as the database.
**Fix:** log only `userId`; resolve the email at delivery time in the real
notification integration.

### H4. Servlet-filter wiring: double registration and inverted ordering
Three related problems:

1. **`JwtAuthenticationFilter` is registered twice.** It is a `@Component`
   (auto-registered by Boot as a plain servlet filter at `@Order(2)`) *and* added to
   the Spring Security chain via `addFilterBefore`. `OncePerRequestFilter` prevents
   double execution, but the duplicate registration is a latent trap (e.g. it runs
   for requests that bypass the security chain). Standard fix: expose a
   `FilterRegistrationBean` with `setEnabled(false)` for the component.
2. **The `@Order(0/1/2)` intent doesn't hold.** The Spring Security chain is
   registered at order `-100`, so it runs *before* `RateLimitingFilter(0)` and
   `RequestLoggingFilter(1)`. Consequences: requests rejected by the security layer
   (401) are never rate-limited **and never logged** by `RequestLoggingFilter`.
   Login brute-force is still rate-limited (permitAll traffic passes through to the
   filters), so impact is contained, but the ordering the annotations imply is not
   the ordering in production.
3. **`POST /api/v1/auth/logout` breaks its documented contract.** The controller
   promises "204 regardless of whether the token was already invalid (idempotent)"
   and `AuthService.logout` has a try/catch for invalid tokens — but the JWT filter
   (inside the security chain) rejects any request with an invalid/expired Bearer
   token with 401 before the controller is reached. The graceful no-op path is dead
   code; clients logging out with an expired token get 401 instead of 204.
   **Fix:** skip token rejection (`shouldNotFilter` or pass-through) for
   `/api/v1/auth/logout`, or accept-and-document the 401 behaviour.

---

## Medium

### M1. Refresh-token reuse is not treated as compromise
On `refresh()`, a replayed (already-revoked) token gets a plain 401. OWASP guidance
for rotating refresh tokens is to treat reuse as evidence of theft and revoke the
entire token family (`revokeAllByUserId`) — the machinery already exists but isn't
called on the reuse path.

### M2. Concurrent refresh race — one token can mint two sessions
`refresh()` does read-check-write (`findByTokenHash` → `isRevoked()` → `setRevoked`)
with no locking and no `@Version` on `RefreshToken`. Two concurrent requests with the
same refresh token can both pass the check and both receive fresh token pairs.
**Fix:** atomic guard, e.g. `UPDATE refresh_tokens SET revoked = true WHERE
token_hash = :hash AND revoked = false` and require `1` row affected.

### M3. Token blacklist fails open when Redis is down
If Redis becomes unavailable, `isRevoked()` falls back to the local map — which does
not contain revocations that were recorded in Redis, so logged-out access tokens
become valid again (for up to 15 minutes) on all instances. The fallback also isn't
shared across instances. This is a documented tradeoff, but for a healthcare API,
consider failing closed on Redis errors for the revocation check, or at least
alerting loudly.

### M4. Rate limiting is per-instance, contradicting the stated design
`pom.xml` says Redis is used "for distributed rate limiting and token blacklist", but
`RateLimitingFilter` uses only in-memory bucket4j maps. With N replicas the effective
limit is N× the configured value. Use `bucket4j-redis` or document the limitation.

### M5. CI security scans can never fail the build
- OWASP job: `continue-on-error: true` **and** `-DfailBuildOnError=false`
- Trivy: `exit-code: '0'` on both scans
- CodeQL: `upload: never`, artifact only

All three are purely advisory; a critical CVE or CodeQL finding will not block a
merge, and nothing surfaces in the GitHub Security tab. At minimum, make the OWASP
job fail on CVSS ≥ 7 (the maven plugin is already configured with
`failBuildOnCVSS=7` — the CI flags neutralize it).

### M6. Tests run on H2, production runs on PostgreSQL
`application-test.yml` swaps in H2 with `ddl-auto: create-drop` and Flyway disabled.
Dialect-sensitive constructs are therefore untested against the real engine — notably
`CAST(a.startedAt AS java.time.LocalDate)` and the `(:param IS NULL OR …)` pattern in
`ActivityRepository.findByFilters`, which is a known source of
"could not determine data type of parameter" errors on PostgreSQL. Migrations
themselves are also never exercised in CI. **Fix:** add a Testcontainers-based
integration test profile running Flyway + repository queries against Postgres.

### M7. Missing account-lifecycle features (healthcare/GDPR posture)
There is no password change/reset, no account deletion (right to erasure), and no
email verification. `AuthService.revokeAllUserTokens` exists "e.g. on password
change" but no endpoint calls it — it's dead code today. FK constraints
(`activities.user_id REFERENCES users(id)` without `ON DELETE`) would block user
deletion anyway. Worth an explicit roadmap decision.

### M8. Streak/milestone timezone inconsistency
`SummaryService.getCurrentStreak` is timezone-aware via `X-User-Timezone`, but
`ActivityEventConsumer` computes the streak with `ZoneOffset.UTC` (the event doesn't
carry the user's timezone). A user in UTC−8 can see streak *N* in the API while the
milestone engine computes *N±1*, so milestones may fire early/late or be skipped
(threshold never observed). Also, `occurredAt`/`achievedAt` use unzoned
`LocalDateTime.now()` (server-local), while other code uses explicit UTC — pick one
convention (UTC everywhere) for all timestamps.

---

## Low

- **L1. Misplaced YAML config:** in `application.yml`, `show-sql` and
  `properties.hibernate.{dialect,format_sql}` are nested under `spring.flyway`
  instead of `spring.jpa`. They are silently ignored (harmless today because the
  values match defaults, but misleading).
- **L2. CORS origins not trimmed:** `allowedOrigins.split(",")` — a value like
  `https://a.com, https://b.com` produces `" https://b.com"` which will never match.
- **L3. Swagger UI is unreachable:** springdoc is on the classpath but
  `/swagger-ui/**` and `/v3/api-docs/**` fall under `anyRequest().authenticated()`,
  and the JWT filter only accepts API tokens. Either permit them (non-prod profiles)
  or remove the dependency.
- **L4. Email enumeration on register:** `409 "Email already registered"` reveals
  which emails have accounts (login is correctly opaque). Common tradeoff — flag for
  a deliberate decision; mitigations include a generic response + email notification.
- **L5. Blacklist write for refresh tokens is never read:** `AuthService.refresh`
  adds the used refresh token to `TokenBlacklistService`, but nothing ever consults
  the blacklist for refresh tokens (revocation is checked via the DB). Dead write —
  remove it or add the fast-path check.
- **L6. `JwtSecretValidator` gaps:** it only rejects the two known default strings.
  It does not reject `JWT_SECRET == JWT_REFRESH_SECRET` (which would collapse the
  dual-key isolation) or low-entropy values. jjwt enforces ≥256-bit length, but an
  equality check is cheap and worthwhile.
- **L7. Unused repository methods:** `ActivityRepository.findByUserIdOrderByStartedAtDesc`,
  `findByUserIdAndActivityTypeOrderByStartedAtDesc`, `findByUserIdAndSourceOrderByStartedAtDesc`
  (superseded by `findByFilters`) and `StreakMilestoneRepository.findByUserIdOrderByMilestoneDaysDesc`
  have no production callers. `findByUserIdAndDateRange` is used only by a test.
- **L8. `streak_milestones.triggering_activity_id`** has no FK to `activities` —
  deleting an activity leaves dangling references (may be intentional; document it).
- **L9. Unbounded result set in streak query:** `findDistinctActiveDatesByUserId`
  fetches up to 400 days of distinct dates per event consumed — fine, but it runs on
  *every* activity creation via Kafka. A `COUNT`-based or windowed approach would cut
  consumer load if volume grows.
- **L10. docker-compose has no postgres service:** the app points at
  `host.docker.internal:5432`, so `docker compose up` fails unless Postgres runs on
  the host. Adding a `postgres` service would make local bootstrap one command.

---

## Positive observations

- Dual-key JWTs (separate access/refresh secrets) plus a `type` claim, issuer and
  audience enforcement — token-confusion attacks are cryptographically prevented.
- Refresh tokens are rotated on use and stored only as SHA-256 hashes.
- `JwtSecretValidator` hard-fails startup in the `prod` profile with default secrets.
- Every data access is scoped by `userId` from the authenticated principal
  (`findByIdAndUserId` etc.) — no IDOR surface found in controllers/services.
- Optimistic locking (`@Version`) on `User` and `Activity` with a proper 409 handler.
- X-Forwarded-For is only trusted from an allowlist of proxy IPs (spoof-safe rate
  limiting), and rate-limit buckets are evicted to bound memory.
- Aggregations pushed to the database via projections instead of in-memory folding.
- Global exception handler returns sanitized messages; stack traces never leak.
- Strict security headers (HSTS, CSP `default-src 'none'`, frame-deny, referrer
  policy), actuator locked down to `/health` with `show-details: never`.
- Flyway with `ddl-auto: validate` (schema drift is caught, not papered over).
- Multi-stage Docker build running as a non-root user.
- Kafka producer configured idempotent with `acks=all`; publishing is deliberately
  fire-and-forget so a Kafka outage cannot fail the HTTP write path (documented,
  with the outbox pattern named as the upgrade path).
- Bean Validation is thorough, including cross-field validators
  (`@ValidDateRange`, `@ValidDeviceId`) and a strong password policy.

## Suggested fix order

1. H1 (one-line CORS fix), H3 (one-line log fix)
2. H2 (DLQ naming), H4 (filter registration/ordering cleanup)
3. M1 + M2 together (refresh-token hardening)
4. M5 (make CI scans enforce), M6 (Testcontainers)
5. Remaining mediums/lows as roadmap items.
