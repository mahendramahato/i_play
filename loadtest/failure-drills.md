# Failure drills

Chaos drills run against the Docker Compose stack (3 backends behind nginx,
MySQL, Redis, Prometheus/Grafana) on 2026-09-22/23.

Method: **predict first, then break it, then observe.** The gap between the
prediction and the result is the point — three of the five drills behaved
differently than predicted, and two produced fixes.

| # | Drill | Predicted | Actual | Verdict |
|---|-------|-----------|--------|---------|
| 1 | Kill a backend | nginx routes around it, container auto-restarts | Not properly run — the container had already been stopped manually ~55 min earlier and **nothing alerted anyone**. Prometheus knew (`backend2:8080 down`); no one was told. | **Closed** — see below |
| 2 | Kill Redis | 500 errors, fast failure | **Unbounded hang.** Requests waited ~5 min until Redis returned, then succeeded. Uncached endpoints unaffected. Self-healed with no restart. | Fixed (see below) |
| 3 | Kill MySQL | Cache serves songs; stream fails; backends go unhealthy | Cache served `/api/songs` for exactly the 60s TTL, then hung. `/stream` hung immediately. Backends went unhealthy after ~110s — **but nginx ignored that and kept routing traffic to them.** Clean self-recovery. | Partly open |
| 4 | Kill nginx | Total outage despite healthy backends | Confirmed. All 3 backends healthy and serving internally; nothing reachable from outside. Prometheus/Grafana stayed up (they bypass nginx). | Known SPOF |
| 5 | Pause Redis (frozen, not dead) | May slip past the fixes, since the TCP connection stays open | Held: **200 OK in ~0.21s**, 100 VUs at 134.9 RPS with **0.0% errors**, clean recovery on unpause. | Fixed |

## What got fixed

Drill 2 exposed a cache outage that could take down a service whose data was
entirely intact in MySQL. Three changes:

| Change | Where | Why |
|---|---|---|
| `LoggingCacheErrorHandler` | `config/CacheConfig.java` | Spring's default handler **rethrows** cache errors, turning a cache miss into a failed request. This logs and falls through to MySQL. |
| `DisconnectedBehavior.REJECT_COMMANDS` | `config/CacheConfig.java` | Lettuce **queues** commands while reconnecting by default — the actual cause of the unbounded hang. Now they fail instantly. |
| `spring.data.redis.timeout=200ms` | `application.properties` | Bounds how long a request can wait on the cache. |
| `management.health.redis.enabled=false` | `application.properties` | Actuator's health endpoint includes a Redis check; without this, moving the healthcheck to `/actuator/health` would have recreated the cascade through a different door. |
| Healthcheck → `/actuator/health` | `docker-compose.yml` | `/api/songs` went through the cache, so a Redis blip marked healthy backends unhealthy. |

Before/after on the same drill:

| | Before | After |
|---|---|---|
| `/api/songs`, Redis down | hung ~5 minutes | 200 OK in ~0.2s |
| 100 VUs, Redis down | would exhaust threads | 123 RPS, 0.0% errors |
| Container health | would go unhealthy | stayed healthy |

The backend logged 2,619 cache-connection failures during the outage **while
serving every request successfully** — failures recorded, not propagated.

## Lessons worth keeping

- **Hanging is worse than failing.** A failure returns fast and the system can
  react; a hang holds a request thread hostage. Thread states confirmed it:
  34 threads in `timed-waiting`, one per stuck request. At Tomcat's default
  ~200 threads, a slow dependency becomes a total outage.
- **Dead and frozen are different failure modes.** `REJECT_COMMANDS` handles a
  dependency that's *gone*; only the timeout handles one that's *frozen*. A
  paused container keeps its TCP connection open, so the disconnect logic never
  fires. Frozen is the more realistic production failure.
- **Docker health status and nginx upstream health are independent.** In drill 3
  every backend was `unhealthy` with a failing streak of 22, and nginx routed to
  all of them anyway — it only reacts to *connection* failures, and the backends
  were accepting connections fine, just never answering. The Compose healthcheck
  controls startup ordering and `docker compose ps`; it does **not** pull a sick
  backend out of rotation.
- **A TTL bounds how long a cache can shield you from an outage.** In drill 3 the
  cache covered the MySQL outage for exactly 60 seconds — the TTL — then expired
  and the outage became visible. Longer TTL, longer shield, staler data.
- **Cumulative counters lie.** Checking load distribution by totals showed a
  10:1 imbalance that was leftover history. Always compare a before/after delta.

## Closed

### Alerting — was drill 1's gap, now fixed (2026-09-23)

Drill 1's real finding was that backend2 sat down for ~55 minutes and nothing
told anyone, while Prometheus knew the whole time. That is now closed.

- `alert-rules.yml` — three rules: `BackendDown` (`up == 0`, `for: 1m`,
  critical), `HighErrorRate` (5xx share of requests > 5% over 5m), and
  `HighLatency` (p95 from `http_server_requests_seconds_bucket` > 2s).
- `alertmanager/` — Alertmanager wired to Gmail SMTP, grouping by `alertname`,
  `group_wait: 30s`, `group_interval: 5m`, `repeat_interval: 4h`,
  `send_resolved: true`. The app password is read from a git-ignored
  `smtp_password.txt` via `smtp_auth_password_file`, so the config stays
  committable.
- `prometheus.yml` — `rule_files:` plus an `alerting:` block targeting
  `alertmanager:9093`.

Verified end to end by stopping backend2:

```
inactive → pending (25s) → firing (75s) → email → restored → resolved email
alertmanager_notifications_total{integration="email"}  2
email failures                                          0
```

Gotchas that cost time and are worth not re-learning:

- **Google displays app passwords with spaces** (`abcd efgh ijkl mnop`); the real
  password is the 16 characters without them. The file was 20 bytes (16 + 3
  spaces + newline), which would have failed SMTP auth.
- **Three different reload semantics.** Changing a mounted file's *contents*
  reloads nothing; Prometheus needs `docker compose restart prometheus` to
  re-read rules and `up -d` if the *mounts* themselves changed; Java source
  changes need `--build`.
- **Resolved notifications are batched too.** The alert went inactive in ~75s but
  the all-clear email waited out `group_interval: 5m`. Lower it for faster
  recovery notices, at the cost of more mail while flapping.
- **`for:` is what makes alerting liveable.** The condition was true at 25s but
  did not fire until 60s, so a restarting backend never pages anyone.

## Still open

1. **`/stream` hangs when MySQL is down.** The same unbounded-wait bug fixed for
   Redis, still present on the JDBC side. Needs a connection/query timeout on
   the datasource.
2. **nginx is a single point of failure** (drill 4). Genuinely hard to fix on one
   Docker host; on maxi the realistic mitigation is fast restart via
   `restart: unless-stopped`.
3. **Sick backends stay in rotation** (drill 3). Would need nginx-level health
   checking, or something acting on Docker's health verdict.
