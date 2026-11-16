# Runbook

How to deploy, operate and recover the production stack in
`deploy/docker-compose.prod.yml`. All commands run from the repository root on the host.

```bash
# Used throughout this document.
alias dc='docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod'
```

> **Status of this stack:** the compose file is validated (`docker compose config`) and the
> application is tested with the settings it uses, but the stack as a whole has not been
> brought up end to end. Do the first deploy on a host where a failed start costs nothing,
> and work through "Verify" below before pointing users at it.

## What runs

| Service | Role | Reachable from |
|---|---|---|
| `caddy` | HTTPS termination, automatic certificates | Internet: 80, 443 |
| `app` | The application. API + UI on 8080, health + metrics on 9090 | `caddy` only (8080); compose networks only (9090) |
| `postgres` | System of record | Internal network only |
| `redis` | Revoked tokens, login lockout counters | Internal network only |
| `kafka` | Activity events | Internal network only |
| `prometheus` (optional) | Metrics and alert rules | `127.0.0.1:9091` on the host |

## First deploy

1. **DNS.** Point the hostname at the host. Caddy cannot get a certificate until it resolves.
2. **Firewall.** Allow inbound 80 and 443 only.
3. **Configuration.**
   ```bash
   cp deploy/env.prod.example deploy/.env.prod
   chmod 600 deploy/.env.prod
   ```
   Fill in every required value. Generate each secret separately (`openssl rand -base64 48`).
   Store a copy of this file somewhere safe that is not this host: without
   `GOOGLE_HEALTH_TOKEN_KEY` the stored integration credentials cannot be decrypted, and
   without the JWT secrets every session is invalidated.
4. **Start.**
   ```bash
   dc up -d --build
   dc ps            # every service should reach "healthy"
   ```
   The app applies database migrations on start. It exits immediately, with a `FATAL:`
   message in `dc logs app`, if a secret is missing or still a placeholder.
5. **Monitoring** (recommended): `dc --profile monitoring up -d`.

### Verify

```bash
curl -sS https://<DOMAIN>/                              # the UI shell
curl -sS -o /dev/null -w '%{http_code}\n' https://<DOMAIN>/actuator/prometheus   # must NOT be 200
dc exec app bash -c 'exec 3<>/dev/tcp/127.0.0.1/9090; printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3; cat <&3'
```

Then, in a browser: register an account, confirm the verification email arrives and its
link works, sign out, use "Forgot your password?" and confirm the reset link works. If no
email arrives, see "Email is not being delivered" below.

## Upgrade

```bash
git pull
# set APP_VERSION in deploy/.env.prod to the new version
./deploy/backup.sh                 # see Backups; always before an upgrade with migrations
dc up -d --build app
dc logs -f app                     # watch it start and pass its health check
```

Caddy keeps serving while the app container is replaced; requests during the restart
(typically under a minute) get a 502. There is one instance, so there is no zero-downtime
upgrade.

## Rollback

The previous image is still on the host under its old tag.

```bash
# set APP_VERSION in deploy/.env.prod back to the previous version
dc up -d --no-build app
```

**Migrations do not roll back.** Flyway only moves forward, and the app validates the
schema on start, so an older build may refuse to run against a newer schema. If the
release you are leaving added a migration:

1. Stop the app: `dc stop app`.
2. Restore the backup taken before the upgrade (see below). Data written since is lost.
3. Start the previous version.

Decide when to roll back before you deploy, not during. Reasonable triggers: the app does
not become healthy within 5 minutes; `HighServerErrorRate` fires; sign-in or logging a
workout fails.

## Backups

PostgreSQL is the only state that matters. Redis holds short-lived entries that expire on
their own, and Kafka holds events already recorded in the database's outbox.

```bash
# Back up to ./backups (custom format, compressed). Store the file OFF this host.
./deploy/backup.sh

# Restore into an empty database. Stop the app first.
dc stop app
dc exec -T postgres dropdb -U activity_tracker activity_tracker
dc exec -T postgres createdb -U activity_tracker activity_tracker
dc exec -T postgres pg_restore -U activity_tracker -d activity_tracker < backups/<file>.dump
dc start app
```

Schedule `deploy/backup.sh` daily (cron) and copy the result to other storage. **Restore a
backup into a scratch database at least once** — a backup that has never been restored is
not known to work. Nothing in this repository schedules backups for you.

Also keep off-host: `deploy/.env.prod`, and the `caddy-data` volume if you want to avoid
re-issuing certificates.

## Monitoring and alerts

With `--profile monitoring`, Prometheus scrapes the app every 15 seconds and evaluates
`deploy/prometheus/alerts.yml`. View it through an SSH tunnel:

```bash
ssh -L 9091:127.0.0.1:9091 <host>      # then open http://localhost:9091/alerts
```

| Alert | Means | First thing to check |
|---|---|---|
| `AppDown` | Scrapes failing for 2 min | `dc ps`, `dc logs --tail 200 app` |
| `HighServerErrorRate` | >5% of requests return 5xx | App logs; is PostgreSQL healthy? |
| `SlowRequests` | p95 latency above 1 s | `DatabasePoolSaturated`; host CPU and memory |
| `DatabasePoolSaturated` | Requests waiting for a DB connection | Long-running queries in `pg_stat_activity` |
| `HeapNearlyFull` | Heap above 90% for 10 min | Raise `APP_MEMORY_LIMIT` |
| `LoginRateLimiting` | Sustained 429s | Abuse, or `TRUSTED_PROXIES` no longer matches Caddy's address |

**Alerts are not delivered anywhere yet.** Prometheus only shows them. To be notified, run
an Alertmanager with a receiver (email, Slack, PagerDuty) and uncomment the `alerting`
block in `deploy/prometheus/prometheus.yml`. Until then, someone has to look.

Health endpoints, on the management port (9090, inside the compose network):

- `/actuator/health/liveness` — the process is running.
- `/actuator/health/readiness` — the app can serve requests (it can reach PostgreSQL).

Redis and SMTP being down does not make the app unhealthy; it degrades instead (below).

## When something is wrong

**The app will not start.** `dc logs app`. A line starting `FATAL:` names the secret that
is missing, still a placeholder, or identical to another. A Flyway error means the schema
and the build disagree — see Rollback.

**Email is not being delivered.** `dc logs app | grep -i email`. `Email delivery failed`
gives the SMTP error (wrong host, port or credentials; sender address not authorised by
the provider). `outbound email is disabled` means `MAIL_ENABLED` is not `true`. Requests
never fail because of mail — forgot-password answers the same either way by design — so
the log is the only place this shows up.

**Redis is down.** The app keeps working. Sign-out and login lockout still take effect on
this instance from memory, and the app logs `Redis unavailable` at ERROR. Restart it:
`dc restart redis`. Entries held only in memory are lost if the app restarts before Redis
is back, which means a token revoked in that window becomes valid again until it expires
(at most 15 minutes for access tokens).

**Kafka is down.** Workouts still save. Their events queue in the `activity_outbox` table
and are published when Kafka returns; rows that exhaust their retries are marked `FAILED`
and logged at ERROR. Streak milestones are computed from those events, so they lag until
it recovers.

**Everyone is getting 429.** The app is treating all traffic as one client, because it is
not trusting Caddy's `X-Forwarded-For`. Check that `TRUSTED_PROXIES` in the compose file
matches Caddy's address on the `edge` network:
`docker inspect activity-tracker-caddy-1 | grep IPAddress`.

**A user is locked out.** Ten failed sign-ins lock an account for 15 minutes. It clears by
itself, or immediately when the user completes a password reset.

**A secret has leaked.**

- `JWT_SECRET` / `JWT_REFRESH_SECRET`: replace both and restart. Every user is signed out.
- `POSTGRES_PASSWORD` / `REDIS_PASSWORD`: change it in the service first
  (`ALTER USER` / restart Redis with the new value), then in `.env.prod`, then restart the app.
- `GOOGLE_HEALTH_TOKEN_KEY`: there is no re-encryption tool. Replacing it makes stored
  Google connections and DeepSeek keys unreadable; users must reconnect and re-enter them.
  Revoke the Google OAuth client's tokens as well.

## Running more than one instance

Not supported by this stack as it stands. Before adding instances:

- Rate limiting is in memory per instance, so limits multiply by the instance count.
  Move it to a shared store.
- Token revocation and login lockout are shared through Redis and fall back to
  per-instance memory when Redis is unreachable; Redis becomes a real dependency.
- Scheduled jobs (token cleanup, Google Health sync) run on every instance.
