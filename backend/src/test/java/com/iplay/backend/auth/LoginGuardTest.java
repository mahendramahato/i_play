package com.iplay.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginGuardTest {

    /** A clock the test can move forward. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();
    private final LoginGuard guard = new LoginGuard(clock);

    @Test
    @DisplayName("a client is blocked after five failures")
    void blocksAfterFiveFailures() {
        for (int i = 0; i < LoginGuard.MAX_FAILURES_PER_CLIENT; i++) {
            assertThat(guard.blocked("1.1.1.1")).isFalse();
            guard.recordFailure("1.1.1.1");
        }
        assertThat(guard.blocked("1.1.1.1")).isTrue();
        assertThat(guard.blocked("2.2.2.2")).isFalse();
    }

    @Test
    @DisplayName("the block lifts once the window has passed")
    void unblocksAfterWindow() {
        for (int i = 0; i < LoginGuard.MAX_FAILURES_PER_CLIENT; i++) guard.recordFailure("1.1.1.1");
        clock.now = clock.now.plus(LoginGuard.WINDOW).plusSeconds(1);
        assertThat(guard.blocked("1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("failures spread across many clients trip the global limit")
    void globalLimit() {
        for (int i = 0; i < LoginGuard.MAX_FAILURES_TOTAL; i++) guard.recordFailure("10.0.0." + i);
        assertThat(guard.blocked("never-seen-before")).isTrue();
    }

    @Test
    @DisplayName("a successful login clears that client's failures")
    void successResets() {
        for (int i = 0; i < LoginGuard.MAX_FAILURES_PER_CLIENT - 1; i++) guard.recordFailure("1.1.1.1");
        guard.recordSuccess("1.1.1.1");
        guard.recordFailure("1.1.1.1");
        assertThat(guard.blocked("1.1.1.1")).isFalse();
    }
}
