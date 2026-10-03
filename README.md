# Soundly

A self-hosted music streaming service: a React player in front of three load-balanced Spring Boot backends, with caching, monitoring, alerting, failure testing and automated deployment to a cloud VM.

The live instance at [soundlyonline.com](https://soundlyonline.com) is access-restricted, because it serves a personal music library. Access for a demo is available on request.

![Soundly player](docs/screenshot.jpg)

## Architecture

![Soundly architecture](docs/architecture.svg)

**How a request flows:**

1. Cloudflare terminates TLS and checks identity with Cloudflare Access. Unauthenticated requests never reach the server.
2. A Cloudflare Tunnel carries traffic to the VM over an outbound connection, so the VM has no inbound ports open.
3. nginx serves the built React app and proxies `/api/` to three identical, stateless Spring Boot backends.
4. Each backend reads song metadata from MySQL through a shared Redis cache, and streams audio from disk with HTTP Range support so the player can seek.

**Delivery:** Images are only published from `main`, and only after both test jobs pass. The server pulls both new images and config changes from git, since nginx, Prometheus and alerting configuration are mounted from the repo rather than built into images. Each image is tagged with its commit SHA, so any deploy can be rolled back to an exact build.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite 8 |
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Spring Cache |
| Data | MySQL 8, Redis 7 |
| Edge | nginx, Cloudflare Tunnel, Cloudflare Access |
| Observability | Micrometer, Prometheus, Grafana, Alertmanager |
| Delivery | Docker Compose, GitHub Actions, GitHub Container Registry, systemd |
| Testing | JUnit 5, Mockito, MockMvc, H2, k6 |

## Engineering highlights

Each of these came from measuring the system rather than assuming how it behaves.

**A cache outage could take down the whole API, and was fixed.**
Chaos drills showed that with Redis down, requests to the cached endpoint hung for the entire outage (around 5 minutes in testing) instead of failing over. The Redis client queues commands while it reconnects, and Spring's default cache error handler rethrows. The fix was three changes: a logging error handler that falls back to MySQL, rejecting commands while disconnected, and a 200 ms timeout for a Redis that is frozen rather than dead. Afterwards, with Redis down, the API answered in about 0.2 s with 0% errors under 100 concurrent users. A regression test guards this fix, and it was checked by confirming that the test **fails** when the fix is removed.

**Caching the database query made no measurable difference, and that was the useful result.**
Adding Redis left p95 latency exactly where it was (272 ms). The database lookup it removed took only 1-3 ms of each request, so it had never been the bottleneck. An optimization can only save the time spent in the thing it optimizes.

**A 10x slowdown turned out to be the environment, not the architecture.**
The full Docker stack on macOS ran at about 141 requests per second, versus 1,440 for the same backend running natively on the same machine. Comparing like with like traced the gap to Docker Desktop's virtualization layer, especially bind-mounted audio files, rather than to the load balancer or the database.

**Load balancing exposed how nginx caches DNS.**
nginx resolves upstream hostnames once at startup. When containers were recreated with new IPs, it marked two of the three backends unreachable and sent every request to the one that was left. Health-check-gated startup ordering and a deploy step that restarts nginx only when backends are replaced fixed it. Distribution was verified as even: 1,485, 1,494 and 1,414 requests across the three backends.

Full write-ups: [failure drills](loadtest/failure-drills.md) · [load-test results](loadtest/results.md)

## Testing

26 tests, none of which need MySQL, Redis or Docker to run, so they run unchanged in CI.

| Suite | Scope |
|---|---|
| `SongFileNameTest` | Deriving artist and title from file names |
| `SongControllerTest` | HTTP contract: status codes, Range requests (206), path-traversal refusal |
| `SongLibraryTest` | Syncing the music folder with the database, on in-memory H2 |
| `CacheDegradationTest` | Regression test for the Redis outage: serves from the database when the cache is unreachable |

```bash
cd backend && ./mvnw test
```

## Observability and alerting

Each backend exposes Micrometer metrics at `/actuator/prometheus`. Prometheus scrapes all three, Grafana shows JVM and HTTP metrics per instance, and Alertmanager sends an email when a backend goes down, when the 5xx rate goes above 5%, or when p95 latency goes above 2 s. It sends another email when the problem resolves. Grafana, Prometheus and Alertmanager listen only on localhost and are reached over SSH.

## Security

- **Identity at the edge:** Cloudflare Access allows only approved emails. Everyone else is stopped at Cloudflare.
- **No open ports:** the tunnel connects outbound, and the site itself listens only on `127.0.0.1`.
- **Secrets stay out of git:** database and SMTP credentials live only on the server. The repo ships `.env.example` as a template.
- **Path traversal:** stream requests that resolve outside the music folder are refused, and there is a test for it.

## API

| Method | Path | Returns |
|---|---|---|
| GET | `/api/songs` | Song list (cached in Redis) |
| GET | `/api/songs/{id}` | One song |
| GET | `/api/songs/{id}/stream` | Audio, with Range support |
| GET | `/api/songs/{id}/cover` | Embedded cover art |

The API is read-only. Songs are added by placing audio files in the music folder. On startup, the backend reads their tags and syncs the database.

## Running locally

```bash
cp .env.example .env               # set DB_PASSWORD
# put audio files in backend/music/ ("Artist - Title.mp3", or tagged files)
docker compose up -d --build       # http://localhost
```

Frontend with hot reload, using the backend from the stack above:

```bash
echo "VITE_API_URL=http://localhost/api/songs" > .env.local
npm install && npm run dev         # http://localhost:5173
```

## Deployment

The server runs the same Compose stack with `docker-compose.prod.yml` layered on top, which swaps local builds for the published images:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

[`deploy/`](deploy/) contains the deploy script and the systemd service and timer that run it every five minutes.

## Known limitations

- nginx is a single point of failure. Three backends don't help if the one proxy in front of them stops.
- If MySQL is down, `/stream` waits instead of failing fast. This is the database-side version of the Redis issue described above.
- The player has no song list yet. Prev and Next are the only way to move between tracks.
