# Fitbit (Charge 6) Integration — Design Document

**Status:** Proposal (no application code written yet — awaiting approval)
**Scope:** Personal, single-user use (the repo owner only)
**Target API:** Google Health API (`https://health.googleapis.com`)
**Device:** Fitbit Charge 6

---

## 1. Why the Google Health API (not the Fitbit Web API)

The legacy **Fitbit Web API is deprecated in September 2026** and is being replaced
by the **Google Health API**. Fitbit accounts already had to migrate to Google
accounts (deadline May 2026). Therefore this integration is built directly on the
Google Health API and Google OAuth 2.0 — building on the old Fitbit API would be a
dead end.

## 2. How the personal use case simplifies everything

Because **only the owner uses this**, the hard production hurdles disappear:

| Production requirement | Why it does not apply here |
|---|---|
| Google privacy/security review for `googlehealth.*` (Restricted) scopes | Only required to serve *other* users in Production. We stay in **Testing**. |
| 100-user cap / third-party security audit | We have exactly 1 user. |
| Public HTTPS webhook endpoint | Not needed — we **poll** on a schedule instead of receiving pushes. |

### The one accepted trade-off
An OAuth app in **Testing** status with an **External** user type has its **refresh
token revoked every 7 days**. The owner uses a regular `@gmail.com` (not Google
Workspace), so the "Internal" no-expiry path is unavailable. We therefore design for
**weekly re-consent**: when the refresh token stops working, the system flags the
connection as `NEEDS_RECONNECT` and reminds the owner to click "Connect" again.

## 3. High-level flow

```
   ┌─────────┐   1. click "Connect"    ┌───────────────────┐
   │  Owner  │ ──────────────────────▶ │  Google OAuth      │
   └─────────┘                         │  consent screen    │
        ▲                              └───────────────────┘
        │ 4. weekly reconnect reminder          │ 2. auth code
        │    (when token expires)               ▼
   ┌──────────────────────────┐        ┌───────────────────┐
   │  Activity Tracker (this   │ ◀──────│  callback endpoint │
   │  Spring Boot app)         │  3. store tokens          │
   │                           │        └───────────────────┘
   │  ┌─────────────────────┐  │
   │  │ Scheduled poller    │  │  5. every N minutes/hours:
   │  │ (@Scheduled job)    │──┼──▶ GET https://health.googleapis.com/... 
   │  └─────────────────────┘  │      (refresh access token as needed)
   │           │               │
   │           ▼               │  6. map + dedupe + save
   │  ┌─────────────────────┐  │
   │  │ activities table    │  │      source = IOT
   │  │ (existing)          │  │      device_id = "fitbit-charge-6"
   │  └─────────────────────┘  │
   └──────────────────────────┘
```

No public endpoint is required for polling; the app can run locally, on a home
server, or a small VPS. (The OAuth callback in step 2–3 only needs to be reachable
by the owner's own browser during the one-time/weekly connect, so `localhost` works.)

## 4. New components to add

### 4.1 Configuration (`application.yml` → `app.integrations.google-health`)
- `client-id`, `client-secret` — from a Google Cloud project (OAuth client).
- `redirect-uri` — e.g. `http://localhost:8080/api/v1/integrations/google-health/callback`.
- `scopes` — only what the Charge 6 provides and we use (see §6).
- `poll-interval` — how often to pull (default e.g. hourly).
- `device-label` — stored in `activities.device_id` (default `fitbit-charge-6`).

Secrets come from environment variables, consistent with how the app already handles
the JWT secret — never committed.

### 4.2 New entity + table: `GoogleHealthConnection`
Stores the single owner's tokens and status.

| Column | Purpose |
|---|---|
| `id` | PK |
| `user_id` | FK → users (the owner) |
| `access_token` | encrypted; short-lived (~1h) |
| `refresh_token` | encrypted; ~7-day life in Testing mode |
| `token_expires_at` | when to refresh |
| `scopes` | granted scopes |
| `status` | `CONNECTED` / `NEEDS_RECONNECT` |
| `last_synced_at` | watermark for incremental polling |
| `connected_at`, `updated_at` | audit |

Tokens encrypted at rest (AES); the app already pulls secret material from env, so the
encryption key follows the same pattern.

### 4.3 New controller: `GoogleHealthIntegrationController`
- `GET  /api/v1/integrations/google-health/connect` → redirects owner to Google consent.
- `GET  /api/v1/integrations/google-health/callback` → exchanges auth code for tokens, saves connection.
- `GET  /api/v1/integrations/google-health/status` → shows `CONNECTED` / `NEEDS_RECONNECT` + `last_synced_at`.
- `DELETE /api/v1/integrations/google-health` → disconnect (revoke + delete tokens).

### 4.4 New services
- `GoogleHealthOAuthService` — builds the consent URL, exchanges the code, refreshes
  access tokens, detects refresh-token revocation (→ set `NEEDS_RECONNECT`).
- `GoogleHealthClient` — thin REST client for `https://health.googleapis.com` data reads.
- `GoogleHealthSyncService` — the `@Scheduled` poller: pulls data since
  `last_synced_at`, maps it, dedupes, saves activities, advances the watermark.

### 4.5 Reuse what already exists
- **Reuse** `NotificationService` to emit the weekly "reconnect" reminder
  (currently logs; same mechanism as streak milestones).
- **Reuse** the existing `Activity` storage path so imported workouts flow through the
  same Kafka event → streak-milestone logic you already have.
- **Reuse** Spring's `@Scheduled` (no new infra) for polling.

## 5. Avoiding duplicates (idempotency)

Each Google Health record has a stable identifier. To prevent re-importing the same
workout on every poll, add a nullable column to `activities`:

- `external_id VARCHAR(255)` — the source record id (null for MANUAL entries)
- partial UNIQUE index on `(user_id, external_id)` where `external_id IS NOT NULL`

On each poll we upsert by `external_id`: insert if new, update if the source record
changed, skip if unchanged.

## 6. Data mapping — Google Health → existing `Activity`

The Charge 6 signals map cleanly onto fields you already have:

| Google Health data type | → `activities` column | Notes |
|---|---|---|
| `exercise` (workout session) | `activity_type`, `started_at`, `ended_at`, `duration_minutes` | type via mapping table below |
| `distance` | `distance_km` | meters → km |
| `steps` | `steps` | |
| `heartRate` (session avg) | `heart_rate_avg` | |
| `totalCalories` / `caloriesInHeartRateZone` | `calories_burned` | |
| (source) | `source = IOT`, `device_id = "fitbit-charge-6"` | constant |
| (source record id) | `external_id` | for dedupe |

### Activity-type mapping (Google → our `ActivityType` enum)
`WALKING, RUNNING, YOGA, CYCLING, SWIMMING, STRENGTH_TRAINING, STRETCHING` map
directly where names match; everything else falls back to **`OTHER`**. A small
lookup map handles this, so unknown Google exercise types never break the import.

## 7. Optional: richer health data (new tables)

The Charge 6 also captures data your schema has **no home for** today. These are
optional and can be deferred; if wanted, add dedicated tables rather than overloading
`activities`:

| Data | Google Health type | Suggested table |
|---|---|---|
| Sleep stages/duration | `sleep` | `sleep_sessions` |
| Blood oxygen | `dailyOxygenSaturation` | `daily_health_metrics` |
| Heart-rate variability | `dailyHeartRateVariability` | `daily_health_metrics` |
| Resting heart rate | `dailyRestingHeartRate` | `daily_health_metrics` |
| Skin temperature | sleep temperature derivations | `daily_health_metrics` |

**Recommendation:** ship Phase 1 (workouts → `activities`) first; add these only if
you actually want sleep/recovery tracking, since they also require new API endpoints
and UI/queries to be useful.

## 8. Database migrations

- `V2__google_health_connection.sql` — create `google_health_connections`.
- `V3__activities_external_id.sql` — add `external_id` + partial unique index.
- (Phase 2, optional) `V4__health_metrics.sql` — sleep + daily metrics tables.

Flyway is already configured, so these slot in as new versioned migrations.

## 9. Security & secrets

- OAuth `client-secret` and the token-encryption key come from **environment
  variables** (same approach as the existing JWT secret) — nothing committed.
- Tokens encrypted at rest.
- The integration endpoints sit behind the app's existing JWT auth, so only the
  logged-in owner can connect/disconnect/view status.

## 10. Proposed phasing

1. **Phase 1 — Connect + import workouts**
   OAuth connect/callback, token storage + refresh, scheduled poller, dedupe,
   map `exercise/distance/steps/heartRate/calories` → `activities`. Weekly
   reconnect reminder via `NotificationService`.
2. **Phase 2 (optional) — Richer health data**
   Sleep, SpO2, HRV, resting HR, skin temperature into new tables + read endpoints.

## 11. Open prerequisites for the owner (outside the code)

1. Create a **Google Cloud project**, enable the Health API, create an **OAuth client
   (Web application)**, and add yourself as a **test user** on the consent screen.
2. Add the redirect URI (e.g. `http://localhost:8080/.../callback`).
3. Provide `client-id` / `client-secret` to the app via environment variables.
4. Accept the **weekly reconnect** behavior (consequence of Testing mode + consumer
   Gmail).

---

*Once this approach is approved, Phase 1 can be implemented behind the existing
auth, with new Flyway migrations and unit tests consistent with the current codebase.*
