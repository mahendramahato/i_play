package com.iplay.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The lock end to end, through the real filter and controller. Each test uses
 * its own client address so the brute-force counters don't interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.password=open-sesame", "app.secret=test-secret"})
class AuthIntegrationTest {

    @Autowired
    MockMvc mvc;

    private MvcResult login(String password, String client) throws Exception {
        return mvc.perform(post("/api/login").header("X-Forwarded-For", client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    @Test
    @DisplayName("songs, audio and covers all need a session")
    void musicIsLocked() throws Exception {
        mvc.perform(get("/api/songs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/songs/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/songs/1/stream")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/songs/1/cover")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("health and session checks stay open")
    void openEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authEnabled").value(true))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @DisplayName("the wrong password is refused and sets no cookie")
    void wrongPassword() throws Exception {
        MvcResult r = login("nope", "203.0.113.1");
        org.assertj.core.api.Assertions.assertThat(r.getResponse().getStatus()).isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(r.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("the right password sets an HttpOnly cookie that unlocks the music")
    void rightPasswordUnlocks() throws Exception {
        MvcResult r = login("open-sesame", "203.0.113.2");
        String setCookie = r.getResponse().getHeader("Set-Cookie");
        org.assertj.core.api.Assertions.assertThat(r.getResponse().getStatus()).isEqualTo(204);
        org.assertj.core.api.Assertions.assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax");

        Cookie session = r.getResponse().getCookie(SessionTokens.COOKIE);
        mvc.perform(get("/api/songs").cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/api/session").cookie(session)).andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    @DisplayName("behind HTTPS (per the proxy header) the cookie is marked Secure")
    void secureBehindHttpsProxy() throws Exception {
        MvcResult r = mvc.perform(post("/api/login").header("X-Forwarded-For", "203.0.113.3")
                        .header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"open-sesame\"}"))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(r.getResponse().getHeader("Set-Cookie")).contains("Secure");
    }

    @Test
    @DisplayName("a forged cookie is rejected")
    void forgedCookie() throws Exception {
        mvc.perform(get("/api/songs").cookie(new Cookie(SessionTokens.COOKIE, "9999999999.deadbeef")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("after five wrong passwords even the right one is refused for a while")
    void bruteForceBlocked() throws Exception {
        for (int i = 0; i < LoginGuard.MAX_FAILURES_PER_CLIENT; i++) login("wrong", "203.0.113.4");
        mvc.perform(post("/api/login").header("X-Forwarded-For", "203.0.113.4")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"open-sesame\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("logout clears the cookie")
    void logoutClearsCookie() throws Exception {
        mvc.perform(post("/api/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }
}
