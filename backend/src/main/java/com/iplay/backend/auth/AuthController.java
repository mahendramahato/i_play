package com.iplay.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    record LoginRequest(String password) {
    }

    private final AuthSettings settings;
    private final SessionTokens tokens;
    private final LoginGuard guard;

    public AuthController(AuthSettings settings, SessionTokens tokens, LoginGuard guard) {
        this.settings = settings;
        this.tokens = tokens;
        this.guard = guard;
    }

    /** Tells the frontend whether to show the sign-in screen. */
    @GetMapping("/session")
    public Map<String, Boolean> session(HttpServletRequest request) {
        boolean signedIn = !settings.enabled() || tokens.isValid(SessionTokens.fromRequest(request));
        return Map.of("authEnabled", settings.enabled(), "authenticated", signedIn);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody(required = false) LoginRequest body,
                                                     HttpServletRequest request) {
        if (!settings.enabled()) {
            return ResponseEntity.noContent().build();
        }
        String client = clientOf(request);
        if (guard.blocked(client)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(LoginGuard.WINDOW.toSeconds()))
                    .body(Map.of("error", "Too many attempts. Try again later."));
        }
        if (body == null || !settings.matches(body.password())) {
            guard.recordFailure(client);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Wrong password."));
        }
        guard.recordSuccess(client);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie(tokens.issue(), SessionTokens.LIFETIME, request).toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, request).toString())
                .build();
    }

    private static ResponseCookie cookie(String value, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie.from(SessionTokens.COOKIE, value)
                .httpOnly(true)
                // TLS ends at Caddy, so the backend sees plain HTTP; trust the proxy's header.
                .secure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    /** Caddy sets X-Forwarded-For to the real client address; nginx passes it through. */
    static String clientOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
