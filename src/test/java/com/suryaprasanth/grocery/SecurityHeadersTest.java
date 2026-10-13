package com.suryaprasanth.grocery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Phase 10 security review: browser security headers and session handling at sign-up. */
class SecurityHeadersTest extends ApiTestSupport {

    @Test
    @DisplayName("Pages get a Content-Security-Policy that forbids framing, plus nosniff, DENY and a referrer policy")
    void pageHeaders() throws Exception {
        MvcResult r = mvc.perform(get("/index.html")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String csp = r.getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).contains("frame-ancestors 'none'").contains("object-src 'none'").contains("connect-src 'self'");
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getResponse().getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    @DisplayName("API answers (orders, invoices...) are never stored in caches; error answers get the headers too")
    void apiNoStore() throws Exception {
        assertThat(call("GET", "/api/categories", null, null).getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        MvcResult error = call("GET", "/api/orders", null, null);
        assertThat(status(error)).isEqualTo(401);
        assertThat(error.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(error.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("Signing up gives the browser a NEW session id (an id planted before sign-up is useless: no session fixation)")
    void signUpRotatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();
        MvcResult r = call("POST", "/api/auth/register", session, Map.of("name", "Fixation Test",
                "email", "fixation-" + UUID.randomUUID() + "@test.local", "password", "secret123"));
        assertThat(status(r)).isEqualTo(201);
        assertThat(session.getId()).isNotEqualTo(before);
        assertThat(status(call("GET", "/api/auth/me", session, null))).isEqualTo(200);
    }

    @Test
    @DisplayName("Logging in also gives a new session id, and logging out ends the session")
    void loginRotatesAndLogoutEnds() throws Exception {
        String email = "rotate-" + UUID.randomUUID() + "@test.local";
        call("POST", "/api/auth/register", new MockHttpSession(), Map.of("name", "Rotate", "email", email, "password", "secret123"));
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();
        assertThat(status(call("POST", "/api/auth/login", session, Map.of("email", email, "password", "secret123")))).isEqualTo(200);
        assertThat(session.getId()).isNotEqualTo(before);
        assertThat(status(call("POST", "/api/auth/logout", session, null))).isEqualTo(204);
        assertThat(session.isInvalid()).isTrue();
    }
}
