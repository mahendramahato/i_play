package com.iplay.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single-password lock. Leaving APP_PASSWORD empty turns it off, which is
 * what local development uses.
 *
 * <p>APP_SECRET is required whenever a password is set. Sessions are signed
 * cookies checked by whichever of the three backends gets the request, so all of
 * them must sign with the same key; a per-process random key would log users out
 * every time a request landed on a different backend.
 */
@Component
public class AuthSettings {

    private final String password;
    private final byte[] secret;

    public AuthSettings(@Value("${app.password:}") String password,
                        @Value("${app.secret:}") String secret) {
        this.password = password == null ? "" : password;
        if (!this.password.isEmpty() && (secret == null || secret.isBlank())) {
            throw new IllegalStateException(
                    "APP_SECRET must be set when APP_PASSWORD is set: every backend has to sign sessions with the same key");
        }
        this.secret = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
    }

    public boolean enabled() {
        return !password.isEmpty();
    }

    byte[] secret() {
        return secret;
    }

    /** Constant-time comparison, so response timing can't reveal how much of a guess was right. */
    boolean matches(String candidate) {
        if (!enabled() || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(candidate), sha256(password));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
