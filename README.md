# Activity Tracker

A personal health-activity tracker: log workouts by hand, import them from a Fitbit CSV
export or Google Health, and see daily/weekly/monthly summaries, streaks and milestones.
Optional AI features summarise a period and comment on workout notes.

- **Backend:** Java 17, Spring Boot 3.5, PostgreSQL (Flyway), Redis, Kafka (transactional outbox)
- **Frontend:** React 18 + TypeScript + Vite, served by the backend from the same origin
- **Version:** 1.0.0

## Run it locally

Needs Docker. The dev stack includes PostgreSQL, Redis, Kafka, a Kafka UI, a mail catcher
and Ollama (which expects an NVIDIA GPU — remove the `ollama` services if you have none
and set `OLLAMA_ENABLED=false`).

```bash
docker compose up --build
```

| What | Where |
|---|---|
| App | http://localhost:8080 |
| API docs (Swagger) | http://localhost:8080/swagger-ui.html |
| Caught emails (password reset, verification) | http://localhost:8025 |
| Kafka UI | http://localhost:8081 |

The dev stack uses default passwords and publishes every port. Do not expose it.

### Without Docker

```bash
# backend (needs PostgreSQL on localhost:5432; Redis and Kafka are optional)
mvn spring-boot:run

# frontend dev server with hot reload, proxying /api to :8080
cd ui && npm ci && npm run dev
```

## Test

```bash
mvn verify                      # unit + integration tests, Spotless, Checkstyle
cd ui && npm test && npm run build
```

The PostgreSQL-backed tests use Testcontainers and are skipped when Docker is not
running. `mvn spotless:apply` fixes formatting failures.

## Deploy to production

Use `deploy/docker-compose.prod.yml`, not the dev compose file. It runs the app with the
`prod` profile behind Caddy (automatic HTTPS), keeps the data stores on an internal
network, and refuses to start until every secret is set.

```bash
cp deploy/env.prod.example deploy/.env.prod     # fill it in
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod up -d --build
```

**Read [deploy/RUNBOOK.md](deploy/RUNBOOK.md) first.** It covers first deploy, upgrades,
rollback, backups, monitoring and what to do when things break.

## Configuration

All configuration is through environment variables. The `prod` profile
(`SPRING_PROFILES_ACTIVE=prod`) makes the starred ones mandatory and rejects the
placeholder values that ship in this repository.

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` ★ | PostgreSQL connection | local dev DB |
| `JWT_SECRET` ★, `JWT_REFRESH_SECRET` ★ | Token signing keys; must differ | dev placeholders |
| `GOOGLE_HEALTH_TOKEN_KEY` ★ | Encrypts stored Google OAuth tokens **and** users' DeepSeek API keys | dev placeholder |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Shared token revocation and login lockout. Falls back to in-memory if unreachable | `localhost:6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Activity events | `localhost:9092` |
| `MANAGEMENT_PORT` | Separate port for health and metrics. Metrics are only served when this is set | unset (same port, metrics closed) |
| `TRUSTED_PROXIES` | Comma-separated proxy IPs allowed to set `X-Forwarded-For`. **Set this behind a load balancer**, or all clients share one rate-limit bucket | empty |
| `CORS_ORIGINS` | Allowed browser origins | `http://localhost:3000` |
| `MAIL_ENABLED` | Send password-reset and verification emails | `false` |
| `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP settings (any provider) | — |
| `PUBLIC_BASE_URL` | Origin used in emailed links | `http://localhost:8080` |
| `AI_PROVIDER` | `ollama` (local model) or `deepseek` (each user's own key) | `ollama` |
| `OLLAMA_ENABLED`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | Local model settings | enabled, `localhost:11434` |
| `GOOGLE_HEALTH_ENABLED`, `_CLIENT_ID`, `_CLIENT_SECRET`, `_REDIRECT_URI` | Google Health import | disabled |
| `LOGIN_MAX_ATTEMPTS`, `LOGIN_LOCKOUT_MINUTES` | Per-account failed-login lockout | 10 attempts, 15 min |
| `OUTBOX_ENABLED` | Publish activity events to Kafka | `true` |

## Things to know before going public

- **Email verification does not gate sign-in.** An unverified account works normally; the
  profile shows the status and can resend the link.
- **Access and refresh tokens are kept in `localStorage`.** A strict Content-Security-Policy
  limits the exposure, but an XSS bug would expose them. Moving to httpOnly cookies means
  re-enabling CSRF protection in the backend.
- **With `AI_PROVIDER=deepseek`, workout notes are sent to DeepSeek's API.** Decide whether
  that is acceptable for the data you hold.
- **One instance.** Rate limiting is per instance, and token revocation and login lockout
  only span instances while Redis is reachable.

## Repository layout

```
src/main/java/...       backend (controller / service / repository / config / filter)
src/main/resources/     application.yml, application-prod.yml, Flyway migrations
ui/                     React frontend
deploy/                 production compose stack, Caddyfile, Prometheus rules, runbook
scripts/                local CI and SonarQube helpers
.github/workflows/      CI: tests, gitleaks, CodeQL, OWASP dependency check, Trivy, ZAP
```
