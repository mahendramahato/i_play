package com.iplay.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private final AuthSettings settings = new AuthSettings("pw", "secret-a");
    private final SessionTokens tokens = new SessionTokens(settings, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("a freshly issued token is valid")
    void issuedTokenIsValid() {
        assertThat(tokens.isValid(tokens.issue())).isTrue();
    }

    @Test
    @DisplayName("a token from another backend with the same secret is valid (stateless sessions)")
    void anotherInstanceWithSameSecretAccepts() {
        SessionTokens otherBackend = new SessionTokens(new AuthSettings("pw", "secret-a"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(otherBackend.isValid(tokens.issue())).isTrue();
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void differentSecretRejects() {
        SessionTokens otherKey = new SessionTokens(new AuthSettings("pw", "secret-b"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(otherKey.isValid(tokens.issue())).isFalse();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredRejected() {
        String token = tokens.issue();
        SessionTokens later = new SessionTokens(settings,
                Clock.fixed(NOW.plus(SessionTokens.LIFETIME).plus(Duration.ofSeconds(1)), ZoneOffset.UTC));
        assertThat(later.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("extending the expiry without re-signing is rejected")
    void tamperedExpiryRejected() {
        String token = tokens.issue();
        String signature = token.substring(token.indexOf('.') + 1);
        assertThat(tokens.isValid("9999999999." + signature)).isFalse();
    }

    @Test
    @DisplayName("garbage and missing tokens are rejected")
    void garbageRejected() {
        assertThat(tokens.isValid(null)).isFalse();
        assertThat(tokens.isValid("")).isFalse();
        assertThat(tokens.isValid("nodot")).isFalse();
        assertThat(tokens.isValid(".abc")).isFalse();
        assertThat(tokens.isValid("12ab.abc")).isFalse();
    }
}
