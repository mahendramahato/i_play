package com.iplay.backend.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stateless sessions: the cookie holds "expiry.signature", where the signature
 * is an HMAC of the expiry time. Nothing is stored server-side, so any backend
 * can validate a cookie issued by any other.
 */
@Component
public class SessionTokens {

    public static final String COOKIE = "soundly_session";
    public static final Duration LIFETIME = Duration.ofDays(7);

    private final AuthSettings settings;
    private final Clock clock;

    @Autowired
    public SessionTokens(AuthSettings settings) {
        this(settings, Clock.systemUTC());
    }

    SessionTokens(AuthSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public String issue() {
        String expires = Long.toString(clock.instant().plus(LIFETIME).getEpochSecond());
        return expires + "." + sign(expires);
    }

    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        String expires = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        if (!expires.chars().allMatch(Character::isDigit) || expires.length() > 12) {
            return false;
        }
        if (Long.parseLong(expires) <= clock.instant().getEpochSecond()) {
            return false;
        }
        // Constant-time, so timing can't be used to guess the signature byte by byte.
        return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                sign(expires).getBytes(StandardCharsets.UTF_8));
    }

    public static String fromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.secret(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
