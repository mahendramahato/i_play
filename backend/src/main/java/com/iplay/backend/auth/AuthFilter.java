package com.iplay.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Locks the music: song list, audio and cover art all need a valid session when
 * the lock is on. Login, logout, session and health endpoints stay open.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private final AuthSettings settings;
    private final SessionTokens tokens;

    public AuthFilter(AuthSettings settings, SessionTokens tokens) {
        this.settings = settings;
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !settings.enabled() || !request.getRequestURI().startsWith("/api/songs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (tokens.isValid(SessionTokens.fromRequest(request))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Sign in required.\"}");
    }
}
