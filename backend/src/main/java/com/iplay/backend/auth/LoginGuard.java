package com.iplay.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection for the login endpoint: too many failed attempts from
 * one client, or from everyone combined, block further attempts for a while.
 *
 * <p>Counts are per backend. Caddy also rate-limits /api/login per IP in front of
 * all three backends, which is the limit that actually holds across them.
 */
@Component
public class LoginGuard {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_FAILURES_PER_CLIENT = 5;
    static final int MAX_FAILURES_TOTAL = 50;

    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    private final Deque<Instant> allFailures = new ArrayDeque<>();
    private final Clock clock;

    @Autowired
    public LoginGuard() {
        this(Clock.systemUTC());
    }

    LoginGuard(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean blocked(String client) {
        Instant cutoff = clock.instant().minus(WINDOW);
        prune(allFailures, cutoff);
        Deque<Instant> mine = failures.get(client);
        if (mine != null) {
            prune(mine, cutoff);
            if (mine.isEmpty()) {
                failures.remove(client);
                mine = null;
            }
        }
        return (mine != null && mine.size() >= MAX_FAILURES_PER_CLIENT)
                || allFailures.size() >= MAX_FAILURES_TOTAL;
    }

    public synchronized void recordFailure(String client) {
        Instant now = clock.instant();
        failures.computeIfAbsent(client, k -> new ArrayDeque<>()).addLast(now);
        allFailures.addLast(now);
    }

    public synchronized void recordSuccess(String client) {
        failures.remove(client);
    }

    private static void prune(Deque<Instant> q, Instant cutoff) {
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }
}
