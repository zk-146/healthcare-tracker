# Code Functionality Audit Report

**Project:** CallHealth Activity Tracker Backend
**Date:** 2026-04-09
**Stack:** Java 17, Spring Boot 3.2.5, PostgreSQL, Redis, Kafka, JWT Auth

---

## 1. Architecture Overview

A REST API backend for tracking healthcare/fitness activities. The system provides:

- **Authentication** (register, login, token refresh, logout) with JWT access + refresh tokens
- **Activity CRUD** (create, list with filters/pagination, get, update, delete)
- **User Profile** management (view, partial update)
- **Activity Summaries** (daily, weekly, monthly, custom range) with streak tracking
- **Event-driven streak milestones** via Kafka (3, 7, 14, 30, 60, 100, 365 day milestones)

### Component Map

| Layer | Components |
|---|---|
| Controllers | `AuthController`, `ActivityController`, `ProfileController`, `SummaryController` |
| Services | `AuthService`, `ActivityService`, `ProfileService`, `SummaryService`, `TokenBlacklistService`, `ActivityEventPublisher`, `ActivityEventConsumer` |
| Entities | `User`, `Activity`, `RefreshToken`, `StreakMilestone` |
| Filters | `RateLimitingFilter` (Order 0), `RequestLoggingFilter` (Order 1), `JwtAuthenticationFilter` (Order 2) |
| Infra | PostgreSQL (Flyway), Redis (token blacklist), Kafka (events), Docker, GitHub Actions CI |

---

## 2. Functionality Audit

### 2.1 Authentication (`AuthController` + `AuthService`)

| Endpoint | Status | Notes |
|---|---|---|
| `POST /api/v1/auth/register` | Correct | Email normalized, BCrypt hashing, strong password validation, conflict detection |
| `POST /api/v1/auth/login` | Correct | Constant-time comparison via BCrypt, generic error messages prevent user enumeration |
| `POST /api/v1/auth/refresh` | Correct | Token rotation (old refresh revoked), DB-backed + Redis/in-memory blacklist |
| `POST /api/v1/auth/logout` | Correct | Blacklists access token, revokes all user refresh tokens, idempotent |

**Strengths:**
- Separate HMAC keys for access vs. refresh tokens (prevents token type confusion)
- Refresh tokens stored as SHA-256 hashes in DB (limits exposure on DB compromise)
- `JwtSecretValidator` blocks production startup with default secrets
- Scheduled cleanup of expired refresh tokens (hourly)

**Finding F-01 (Low):** `application.yml` sets `auth-requests-per-minute: 300` and `api-requests-per-minute: 1000` — these are development defaults that are very permissive. The `application-prod.yml` correctly tightens them to 10/60 respectively, but if prod profile is not activated, the permissive defaults apply.

**Finding F-02 (Low):** The `@Deprecated` methods `parseToken()` and `isTokenValid()` in `JwtUtil` (lines 93-131) are dead code. No caller references them. They should be removed to reduce surface area.

### 2.2 Activity Management (`ActivityController` + `ActivityService`)

| Endpoint | Status | Notes |
|---|---|---|
| `POST /api/v1/activities` | Correct | Full validation, publishes Kafka event, user-scoped |
| `GET /api/v1/activities` | Correct | Pagination with max-page-size cap, optional filters (type, source, date range) |
| `GET /api/v1/activities/{id}` | Correct | User-scoped lookup via `findByIdAndUserId` |
| `PUT /api/v1/activities/{id}` | Correct | Full replacement, user-scoped, optimistic locking via `@Version` |
| `DELETE /api/v1/activities/{id}` | Correct | User-scoped, returns 204 |

**Strengths:**
- Defence-in-depth page size cap at service layer (line 130-132 of `ActivityService`)
- Optimistic locking prevents silent concurrent overwrites
- DB indexes on `(user_id, started_at DESC)` and `source` support query patterns
- Kafka publish is fire-and-forget — failures don't break the HTTP path

**Finding F-03 (Medium):** The `findByFilters` JPQL query (`ActivityRepository`:46-58) uses `IS NULL` checks for nullable parameters. While functionally correct, this pattern can cause poor query plans in PostgreSQL because the optimizer cannot use indexes effectively when predicates include `OR param IS NULL`. Consider using `JpaSpecificationExecutor` (already extended but unused) to build dynamic queries that omit null-filter predicates entirely.

**Finding F-04 (Low):** `updateActivity()` does a full field replacement (PUT semantics) but uses the same `ActivityRequest` DTO as creation. This means an update requires all mandatory fields even if only changing `notes`. A PATCH endpoint with partial updates would be more ergonomic.

**Finding F-05 (Low):** No `ACTIVITY_UPDATED` or `ACTIVITY_DELETED` events are published. Only creation triggers a Kafka event. Downstream consumers cannot track modifications or deletions.

### 2.3 Profile Management (`ProfileController` + `ProfileService`)

| Endpoint | Status | Notes |
|---|---|---|
| `GET /api/v1/profile` | Correct | User-scoped via auth principal |
| `PUT /api/v1/profile` | Correct | Partial update (null fields ignored), validation on bounds |

**Strengths:**
- Null-safe partial updates — only non-null fields are overwritten
- `@Version` on User entity provides optimistic locking

**Finding F-06 (Low):** Profile update uses `PUT` but implements `PATCH` semantics (null fields are ignored). The HTTP method should be `PATCH` to match the behavior.

**Finding F-07 (Info):** No way to clear optional profile fields once set (e.g., setting `gender` back to null). The null-means-skip pattern prevents field clearing without a separate "clear fields" mechanism.

### 2.4 Summary & Streaks (`SummaryController` + `SummaryService`)

| Endpoint | Status | Notes |
|---|---|---|
| `GET /api/v1/summary?from=&to=` | Correct | Custom range with max 365 days, timezone-aware streak |
| `GET /api/v1/summary/daily` | Correct | Today in user's timezone |
| `GET /api/v1/summary/weekly` | Correct | Monday through today |
| `GET /api/v1/summary/monthly` | Correct | 1st through today |

**Strengths:**
- DB-level aggregation via projection interfaces (no loading full entities)
- Timezone-aware streak calculation via `X-User-Timezone` header
- Max range guard prevents expensive unbounded queries
- Streak algorithm gracefully handles "no activity today" (starts from yesterday)

**Finding F-08 (Medium):** The streak calculation in `getCurrentStreak()` fetches up to 400 days of distinct dates into memory (`SummaryService`:177-178), then iterates in Java. For users with high activity volumes, this query + iteration could be moved entirely to SQL with a recursive CTE or window function for better performance.

**Finding F-09 (Low):** The `SummaryService.getDailySummary()` and `getWeeklySummary()` methods are not annotated with `@Transactional(readOnly = true)`, unlike `getSummary()` which they delegate to. This works because `getSummary()` has the annotation, but it's inconsistent.

### 2.5 Event System (Kafka)

| Component | Status | Notes |
|---|---|---|
| `ActivityEventPublisher` | Correct | Keyed by userId (partition affinity), fire-and-forget, exceptions swallowed |
| `ActivityEventConsumer` | Correct | Detects streak milestones, idempotent via DB unique constraint check |
| `KafkaConfig` | Correct | Auto-creates topics, DLQ configured, retry with backoff |

**Finding F-10 (Medium):** The consumer uses `ZoneOffset.UTC` hardcoded (`ActivityEventConsumer`:64) for streak calculation, ignoring the user's actual timezone. This means milestone detection may not align with the user's local day boundaries — a user in UTC-8 could have their streak miscounted for milestone purposes.

**Finding F-11 (Low):** The `ActivityCreatedEvent` does not include `steps` or `heartRateAvg` fields. If a downstream consumer needs these for analytics, the event payload would need to be extended.

---

## 3. Security Audit

### 3.1 Strengths

- **JWT:** Separate keys for access/refresh, issuer+audience validation, type claim verification, production secret enforcement
- **Password:** BCrypt encoding, 12-128 char requirement with complexity rules (upper, lower, digit, special)
- **Token Blacklist:** Redis-backed (distributed) with in-memory fallback, SHA-256 hashed keys
- **Rate Limiting:** Per-IP with separate auth/general limits, trusted-proxy-aware X-Forwarded-For parsing, idle bucket eviction
- **CORS:** Configurable allowed origins, restricted methods and headers
- **Headers:** HSTS, X-Content-Type-Options, X-Frame-Options: DENY, CSP, Referrer-Policy, Permissions-Policy
- **Data Access:** All activity queries scoped by userId — no horizontal privilege escalation
- **Docker:** Non-root user, minimal JRE image
- **CI/CD:** OWASP dependency check, CodeQL SAST, Trivy container scanning

### 3.2 Findings

**Finding F-12 (Medium):** The `RateLimitingFilter` uses an in-memory `ConcurrentHashMap` for bucket storage. In a multi-instance deployment, each instance maintains separate counters, effectively multiplying the rate limit by the number of instances. Consider using Redis-backed rate limiting (e.g., bucket4j-redis) for distributed enforcement.

**Finding F-13 (Low):** The `RequestLoggingFilter` logs `request.getRequestURI()` which could include user-controlled path segments. While query parameters are excluded (uses `getRequestURI()` not `getRequestURL()` or `getQueryString()`), path-based injection into log messages is mitigated by SLF4J parameterized logging.

**Finding F-14 (Info):** Actuator endpoints beyond `/health` are configured as `denyAll()` (`SecurityConfig`:69). This is good, but the `management.endpoints.web.exposure.include=health` in config already restricts exposure. The defence-in-depth is appreciated.

### 3.3 CSRF

CSRF protection is disabled, which is correct for a stateless JWT API that doesn't use cookies for authentication. Documented with an inline comment.

---

## 4. Data Model Audit

### 4.1 Schema Alignment

The Flyway migration (`V1__initial_schema.sql`) matches the JPA entity definitions. All columns, indexes, constraints, and types are consistent.

### 4.2 Findings

**Finding F-15 (Low):** The `activities.activity_type` column is `VARCHAR(255)` in the migration but the entity uses `@Enumerated(EnumType.STRING)`. Since the longest enum value is `STRENGTH_TRAINING` (17 chars), a `VARCHAR(30)` would be more appropriate and consistent with `source VARCHAR(10)`.

**Finding F-16 (Info):** The `activities` table has no `ON DELETE CASCADE` for the `user_id` FK. Deleting a user would fail if they have activities. This is likely intentional (prevent accidental cascading deletion), but there's no user deletion endpoint anyway.

---

## 5. Validation Audit

| Validator | Coverage | Notes |
|---|---|---|
| `StrongPasswordValidator` | Correct | 12-128 chars, upper, lower, digit, special char required. Reports all failures at once. |
| `DateRangeValidator` | Correct | endedAt > startedAt, duration consistent with elapsed time (60 min tolerance) |
| `DeviceIdValidator` | Correct | deviceId required when source is IOT |
| Jakarta Bean Validation | Correct | All DTO fields have appropriate constraints |
| `GlobalExceptionHandler` | Correct | Handles 400, 401, 404, 409, 500 with consistent JSON response format. Catches optimistic locking and malformed body errors. |

**Finding F-17 (Medium):** The `DateRangeValidator` only validates that `durationMinutes` is not **too large** relative to elapsed time (line 40: `declared > elapsed + tolerance`). It does not check if `durationMinutes` is suspiciously **too small** — e.g., a 10-hour window with a declared duration of 1 minute passes validation. Consider adding a lower-bound check as well.

---

## 6. Test Coverage Audit

### 6.1 Coverage Summary

| Area | Test Class | Verdict |
|---|---|---|
| Auth flow | `AuthControllerTest`, `AuthServiceTest` | Good — register, login, refresh covered |
| Activities CRUD | `ActivityControllerTest`, `ActivityServiceTest` | Partial — update/delete not tested at controller level |
| Profile | `ProfileControllerTest` | Good — but no `ProfileServiceTest` |
| Summary | `SummaryControllerTest`, `SummaryServiceTest` | Good |
| JWT | `JwtUtilTest`, `JwtAuthenticationFilterTest` | Good — cross-validation, expiry, tampering covered |
| Rate Limiting | `RateLimitingFilterTest` | Good — limits, proxy forwarding, eviction |
| Repository queries | `ActivityRepositoryTest` | Partial — aggregation queries tested, filter query not |
| Event consumer | `ActivityEventConsumerTest` | Good — milestone logic covered |
| Validators | `DateRangeValidatorTest`, `DeviceIdValidatorTest` | Good |

### 6.2 Critical Test Gaps

**Finding F-18 (Medium):** No test for the **logout** flow (neither controller nor service level). Token revocation is a critical security feature that should be tested.

**Finding F-19 (Medium):** No test for **cross-user data isolation** — i.e., verifying that User A cannot access User B's activities or profile. The code correctly scopes queries by `userId`, but there's no test asserting this boundary.

**Finding F-20 (Low):** No test for `TokenBlacklistService` in isolation. It's always mocked in other tests. The Redis fallback logic and eviction scheduling are untested.

**Finding F-21 (Low):** No test for `ProfileService`. Only the controller layer is tested via `ProfileControllerTest`.

**Finding F-22 (Low):** No integration tests exist. All controller tests are `@WebMvcTest` slices, and all service tests use mocks. There's no end-to-end test covering auth → activity creation → summary flow.

---

## 7. Configuration & Infrastructure

**Finding F-23 (Low):** `docker-compose.yml` points the app's datasource to `host.docker.internal:5432` rather than a dockerized PostgreSQL service. The compose file starts Redis and Kafka but assumes PostgreSQL is running on the host.

**Finding F-24 (Info):** The Kafka topic uses `replicas(1)` — documented as demo-appropriate. Production should use 3+.

**Finding F-25 (Info):** The `app.kafka.topics.activity-events` topic name and `app.kafka.consumer.group-id` are duplicated between `application.yml` top-level spring config and the `app:` namespace. The consumer properties reference both `spring.kafka.consumer.group-id` and `app.kafka.consumer.group-id`.

---

## 8. Findings Summary

| ID | Severity | Category | Description |
|---|---|---|---|
| F-01 | Low | Config | Dev rate limits (300/1000 rpm) are very permissive; rely on prod profile override |
| F-02 | Low | Code Quality | Deprecated `parseToken()`/`isTokenValid()` in JwtUtil are dead code |
| F-03 | Medium | Performance | `findByFilters` JPQL with nullable params may cause poor PostgreSQL query plans |
| F-04 | Low | API Design | PUT update requires all fields; no PATCH for partial updates |
| F-05 | Low | Feature Gap | No update/delete events published to Kafka |
| F-06 | Low | API Design | Profile update uses PUT but implements PATCH semantics |
| F-07 | Info | API Design | Cannot clear optional profile fields once set |
| F-08 | Medium | Performance | Streak calculation loads 400 days of dates into memory |
| F-09 | Low | Code Quality | Inconsistent `@Transactional(readOnly)` on summary convenience methods |
| F-10 | Medium | Correctness | Kafka consumer hardcodes UTC for streak milestones, ignoring user timezone |
| F-11 | Low | Feature Gap | Event payload missing `steps` and `heartRateAvg` |
| F-12 | Medium | Security | Rate limiting is per-instance (in-memory), not distributed |
| F-13 | Low | Security | Request URI logged — low risk with SLF4J parameterized logging |
| F-14 | Info | Security | Good defence-in-depth on actuator endpoints |
| F-15 | Low | Schema | `activity_type` column oversized (VARCHAR 255 vs needed ~30) |
| F-16 | Info | Schema | No CASCADE on activity→user FK (likely intentional) |
| F-17 | Medium | Validation | DateRangeValidator doesn't check for suspiciously short durations |
| F-18 | Medium | Testing | No test for logout/token revocation flow |
| F-19 | Medium | Testing | No test for cross-user data isolation |
| F-20 | Low | Testing | TokenBlacklistService untested in isolation |
| F-21 | Low | Testing | No ProfileService test |
| F-22 | Low | Testing | No integration tests |
| F-23 | Low | Infra | docker-compose assumes host PostgreSQL |
| F-24 | Info | Infra | Kafka topic replication factor = 1 (dev only) |
| F-25 | Info | Config | Kafka consumer group-id configured in two places |

### By Severity

- **Medium:** 7 findings (F-03, F-08, F-10, F-12, F-17, F-18, F-19)
- **Low:** 12 findings
- **Info:** 6 findings

---

## 9. Overall Assessment

The codebase is **well-structured and production-aware**. It demonstrates strong security practices (separate JWT keys, token rotation, SHA-256 hashed storage, rate limiting, security headers, input validation), clean separation of concerns, and thoughtful error handling. The use of Kafka for async streak detection and Redis for distributed token blacklisting shows appropriate architectural thinking.

The primary areas for improvement are:
1. **Performance:** The `findByFilters` nullable-parameter query pattern and in-memory streak calculation should be optimized before scaling
2. **Correctness:** The hardcoded UTC timezone in the Kafka consumer for milestone detection should use the user's timezone
3. **Testing:** Adding logout tests, cross-user isolation tests, and at least one integration test would significantly increase confidence
4. **Distributed concerns:** Rate limiting should be moved to Redis for multi-instance deployments
