package com.AgentOps;

import com.AgentOps.security.AuthCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthCookieIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void loginSetsScopedHttpOnlyCookiesAndTheyAuthenticateRequests() throws Exception {
        String email = email();
        register(email);
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email))).andExpect(status().isOk()).andReturn().getResponse();
        Cookie access = response.getCookie(AuthCookies.ACCESS);
        Cookie refresh = response.getCookie(AuthCookies.REFRESH);
        assertNotNull(access);
        assertNotNull(refresh);
        assertTrue(access.isHttpOnly());
        assertTrue(refresh.isHttpOnly());
        assertEquals("/", access.getPath());
        assertEquals("/api/auth", refresh.getPath());
        assertEquals("Strict", access.getAttribute("SameSite"));
        assertTrue(access.getMaxAge() > 0);
        mvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void unsafeCookieRequestsRequireCsrfButBearerClientsStillWork() throws Exception {
        var login = register(email());
        Cookie access = login.getCookie(AuthCookies.ACCESS);
        mvc.perform(post("/orders").cookie(access).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        Cookie csrf = csrf();
        mvc.perform(post("/orders").cookie(access, csrf).header("X-XSRF-TOKEN", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders").cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        String bearer = mapper.readTree(login.getContentAsString()).get("accessToken").asText();
        mvc.perform(post("/orders").cookie(access).header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void refreshUsesCookieWithoutBodyAndRotatesBothTokensEvenWithExpiredAccess() throws Exception {
        var login = register(email());
        Cookie refresh = login.getCookie(AuthCookies.REFRESH);
        Cookie expired = new Cookie(AuthCookies.ACCESS, "expired.jwt.token");
        Cookie csrf = csrf();
        var response = mvc.perform(post("/api/auth/refresh").cookie(expired, refresh, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertNotEquals(refresh.getValue(), response.getCookie(AuthCookies.REFRESH).getValue());
        mvc.perform(get("/api/auth/me").cookie(response.getCookie(AuthCookies.ACCESS)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(refresh, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookiesAndRevokesAccess() throws Exception {
        var login = register(email());
        Cookie access = login.getCookie(AuthCookies.ACCESS);
        Cookie csrf = csrf();
        mvc.perform(post("/api/auth/logout").cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized());
    }

    @Test
    void explicitInvalidBearerDoesNotFallBackToCookie() throws Exception {
        var login = register(email());
        mvc.perform(get("/api/auth/me").cookie(login.getCookie(AuthCookies.ACCESS))
                .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshCookieCannotBypassCsrfWithAnAuthorizationHeader() throws Exception {
        var login = register(email());
        String bearer = mapper.readTree(login.getContentAsString()).get("accessToken").asText();
        mvc.perform(post("/api/auth/refresh").cookie(login.getCookie(AuthCookies.REFRESH))
                .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void secureCookieConfigurationAppliesToIssuingAndClearing() {
        var cookies = new AuthCookies(true);
        var response = new MockHttpServletResponse();
        cookies.write(response, new com.AgentOps.dto.response.AuthResponse("access", "Bearer", 30, "refresh", 60, null));
        assertTrue(response.getCookie(AuthCookies.ACCESS).getSecure());
        assertTrue(response.getCookie(AuthCookies.REFRESH).getSecure());
        var cleared = new MockHttpServletResponse();
        cookies.clear(cleared);
        assertTrue(cleared.getCookie(AuthCookies.ACCESS).getSecure());
        assertEquals("/api/auth", cleared.getCookie(AuthCookies.REFRESH).getPath());
        assertEquals(0, cleared.getCookie(AuthCookies.REFRESH).getMaxAge());
    }

    @Test
    void passwordChangeClearsCookiesAndRevokesSession() throws Exception {
        var login = register(email());
        Cookie access = login.getCookie(AuthCookies.ACCESS);
        Cookie csrf = csrf();
        mvc.perform(patch("/api/auth/me").cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password123\",\"password\":\"replacement123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized());
    }

    private Cookie csrf() throws Exception {
        return mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private MockHttpServletResponse register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email))).andExpect(status().isCreated()).andReturn().getResponse();
    }

    private String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
    }

    private String email() { return UUID.randomUUID() + "@example.test"; }
}
