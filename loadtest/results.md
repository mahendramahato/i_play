| Run | Endpoint | VUs | RPS | P95 | Errors | When |
|-----|----------|-----|-----|-----|--------|------|
| native-1backend-sleep | Songs | 20 | 19.6 | 18 ms | 0.0% | 2026-09-21 |
| native-1backend-sleep | Stream | 20 | 19.6 | 21 ms | 0.0% | 2026-09-21 |
| native-1backend-nosleep | Both | 100 | 1392 | 65 ms | 0.0% | 2026-09-21 |
| native-1backend-nosleep | Both | 300 | 1330 | 196 ms | 0.0% | 2026-09-21 |
| native-1backend-nosleep | Both | 600 | 1291 | 325 ms | 0.0% | 2026-09-21 |
| docker-3backend-balanced | Songs | 300 | 144.8 | 272 ms | 0.0% | 2026-09-21 |
| docker-3backend-balanced | Stream | 300 | 144.8 | 3535 ms | 0.0% | 2026-09-21 |
| docker-3backend-redis | Songs | 300 | 133.0 | 272 ms | 0.0% | 2026-09-22 |
| docker-3backend-redis | Stream | 300 | 133.0 | 3972 ms | 0.0% | 2026-09-22 |
| append-test | Songs | 50 | 137.6 | 73 ms | 0.0% | 2026-09-22 21:08 |
| append-test | Cover | 50 | n/a | n/a | n/a | 2026-09-22 21:08 |
| append-test | Stream | 50 | 137.6 | 725 ms | 0.0% | 2026-09-22 21:08 |
| native-1backend-direct | Songs | 300 | 1801.8 | 47 ms | 0.0% | 2026-09-22 21:22 |
| native-1backend-direct | Cover | 300 | n/a | n/a | n/a | 2026-09-22 21:22 |
| native-1backend-direct | Stream | 300 | 1801.8 | 277 ms | 49.9% | 2026-09-22 21:22 |
| docker-3backend-clean | Songs | 300 | 140.8 | 263 ms | 0.0% | 2026-09-22 21:23 |
| docker-3backend-clean | Cover | 300 | n/a | n/a | n/a | 2026-09-22 21:23 |
| docker-3backend-clean | Stream | 300 | 140.8 | 3521 ms | 0.0% | 2026-09-22 21:23 |
| native-1backend-clean | Songs | 300 | 1440.4 | 54 ms | 0.0% | 2026-09-22 21:24 |
| native-1backend-clean | Cover | 300 | n/a | n/a | n/a | 2026-09-22 21:24 |
| native-1backend-clean | Stream | 300 | 1440.4 | 391 ms | 0.0% | 2026-09-22 21:24 |
| with-grafana | Songs | 300 | 132.7 | 307 ms | 0.0% | 2026-09-23 04:22 |
| with-grafana | Cover | 300 | n/a | n/a | n/a | 2026-09-23 04:22 |
| with-grafana | Stream | 300 | 132.7 | 3948 ms | 0.0% | 2026-09-23 04:22 |
| with-grafana | Songs | 300 | 125.3 | 271 ms | 0.0% | 2026-09-23 04:29 |
| with-grafana | Cover | 300 | n/a | n/a | n/a | 2026-09-23 04:29 |
| with-grafana | Stream | 300 | 125.3 | 3742 ms | 0.0% | 2026-09-23 04:29 |
| with-grafana | Songs | 300 | 129.2 | 265 ms | 0.0% | 2026-09-23 04:45 |
| with-grafana | Cover | 300 | n/a | n/a | n/a | 2026-09-23 04:45 |
| with-grafana | Stream | 300 | 129.2 | 3681 ms | 0.0% | 2026-09-23 04:45 |
| with-grafana | Songs | 300 | 139.3 | 262 ms | 0.0% | 2026-09-23 04:50 |
| with-grafana | Cover | 300 | n/a | n/a | n/a | 2026-09-23 04:50 |
| with-grafana | Stream | 300 | 139.3 | 3419 ms | 0.0% | 2026-09-23 04:50 |
| with-grafana | Songs | 300 | 129.9 | 260 ms | 0.0% | 2026-09-23 05:04 |
| with-grafana | Cover | 300 | n/a | n/a | n/a | 2026-09-23 05:04 |
| with-grafana | Stream | 300 | 129.9 | 3667 ms | 0.0% | 2026-09-23 05:04 |
| with-grafana | Songs | 300 | 93.4 | 269 ms | 0.0% | 2026-09-23 05:23 |
| with-grafana | Cover | 300 | n/a | n/a | n/a | 2026-09-23 05:23 |
| with-grafana | Stream | 300 | 93.4 | 3361 ms | 0.0% | 2026-09-23 05:23 |
| drill1-kill-backend | Songs | 100 | 146.2 | 59 ms | 0.0% | 2026-09-23 05:25 |
| drill1-kill-backend | Cover | 100 | n/a | n/a | n/a | 2026-09-23 05:25 |
| drill1-kill-backend | Stream | 100 | 146.2 | 1226 ms | 0.0% | 2026-09-23 05:25 |
| drill2-redis-down-fixed | Songs | 100 | 123.0 | 2687 ms | 0.0% | 2026-09-23 05:39 |
| drill2-redis-down-fixed | Cover | 100 | n/a | n/a | n/a | 2026-09-23 05:39 |
| drill2-redis-down-fixed | Stream | 100 | 123.0 | 672 ms | 0.0% | 2026-09-23 05:39 |
| drill5-redis-paused | Songs | 100 | 134.9 | 368 ms | 0.0% | 2026-09-23 05:57 |
| drill5-redis-paused | Cover | 100 | n/a | n/a | n/a | 2026-09-23 05:57 |
| drill5-redis-paused | Stream | 100 | 134.9 | 838 ms | 0.0% | 2026-09-23 05:57 |
