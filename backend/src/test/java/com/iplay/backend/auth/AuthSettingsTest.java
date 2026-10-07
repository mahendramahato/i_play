package com.iplay.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthSettingsTest {

    @Test
    @DisplayName("no password means the lock is off")
    void disabledWithoutPassword() {
        assertThat(new AuthSettings("", "").enabled()).isFalse();
        assertThat(new AuthSettings(null, null).enabled()).isFalse();
    }

    @Test
    @DisplayName("a password without a shared secret refuses to start")
    void passwordRequiresSecret() {
        // Each backend would otherwise sign with its own key and reject the others' cookies.
        assertThatThrownBy(() -> new AuthSettings("pw", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECRET");
    }

    @Test
    @DisplayName("only the exact password matches")
    void matchesExactly() {
        AuthSettings s = new AuthSettings("correct horse", "k");
        assertThat(s.matches("correct horse")).isTrue();
        assertThat(s.matches("correct hors")).isFalse();
        assertThat(s.matches("Correct horse")).isFalse();
        assertThat(s.matches(null)).isFalse();
    }
}
