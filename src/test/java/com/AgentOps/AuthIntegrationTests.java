package com.AgentOps;

import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.repositories.UserRepository;
import com.AgentOps.security.JwtProperties;
import com.AgentOps.services.AuthSessionService;
import com.AgentOps.repositories.AuthSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.AgentOps.exceptions.InvalidRefreshTokenException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AuthIntegrationTests.RoleTestController.class)
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired JwtProperties properties;
    @Autowired AuthSessionService sessions;
    @Autowired AuthSessionRepository sessionRepository;

    @Test
    void registrationLoginAndCurrentUserWorkWithRealSignedJwt() throws Exception {
        String email = email();
        String response = register(email, ",\"role\":\"ADMIN\"");
        var json = mapper.readTree(response);
        String token = json.get("accessToken").asText();
        assertEquals("Bearer", json.get("tokenType").asText());
        assertEquals("CUSTOMER", json.get("user").get("role").asText());
        assertFalse(response.contains("password"));
        assertFalse(response.contains("password123"));
        User stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertNotEquals("password123", stored.getPasswordHash());
        assertTrue(passwords.matches("password123", stored.getPasswordHash()));
        assertEquals(stored.getId().toString(), decoder.decode(token).getSubject());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email.toUpperCase(), "password123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void duplicateEmailIgnoringCaseReturnsConflict() throws Exception {
        String email = email();
        register(email, "");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email.toUpperCase(), "password123")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidRegistrationReturnsFieldErrorsWithoutPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("invalid", "short")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("short"))));
    }

    @Test
    void unknownAccountAndWrongPasswordReturnSameSafeError() throws Exception {
        String email = email();
        register(email, "");
        for (String attemptedEmail : new String[] { email, email() }) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(attemptedEmail, "incorrect")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        }
    }

    @Test
    void missingMalformedAndTamperedTokensReturnUnauthorizedProblem() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        String token = mapper.readTree(register(email(), "")).get("accessToken").asText();
        int signature = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signature) + (token.charAt(signature) == 'A' ? "B" : "A") + token.substring(signature + 1);
        for (String invalid : new String[] { "invalid.jwt.token", tampered }) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.instance").value("/api/auth/me"));
        }
    }

    @Test
    void expiredWrongIssuerAndMissingExpirationTokensAreRejected() throws Exception {
        Instant now = Instant.now();
        String issued = mapper.readTree(register(email(), "")).get("accessToken").asText();
        Jwt valid = decoder.decode(issued);
        for (String kind : new String[] { "expired", "issuer", "expiration", "role", "subject", "session" }) {
            JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                    .issuer(kind.equals("issuer") ? "other-app" : properties.issuer())
                    .subject(kind.equals("subject") ? "invalid" : valid.getSubject())
                    .issuedAt(now.minusSeconds(600))
                    .claim("role", kind.equals("role") ? "SUPERUSER" : "CUSTOMER")
                    .claim("sid", kind.equals("session") ? "99999999" : valid.getClaimAsString("sid"));
            if (!kind.equals("expiration")) {
                claims.expiresAt(kind.equals("expired") ? now.minusSeconds(120) : now.plusSeconds(600));
            }
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void roleAuthoritiesEnforceMethodSecurity() throws Exception {
        String token = mapper.readTree(register(email(), "")).get("accessToken").asText();
        mvc.perform(get("/auth-tests/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        User admin = new User();
        admin.setEmail(email());
        admin.setPasswordHash(passwords.encode("password123"));
        admin.setRole(UserRole.ADMIN);
        users.saveAndFlush(admin);
        mvc.perform(get("/auth-tests/admin").header("Authorization", "Bearer " + sessions.createSession(admin).accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void deletedUserCannotFetchProfile() throws Exception {
        String email = email();
        String token = mapper.readTree(register(email, "")).get("accessToken").asText();
        users.delete(users.findByEmailIgnoreCase(email).orElseThrow());
        users.flush();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenStoresOnlyHashAndRejectsReplay() throws Exception {
        var original = mapper.readTree(register(email(), ""));
        String oldRefresh = original.get("refreshToken").asText();
        long sessionId = Long.parseLong(decoder.decode(original.get("accessToken").asText()).getClaimAsString("sid"));
        String oldHash = sessionRepository.findById(sessionId).orElseThrow().getRefreshTokenHash();
        assertEquals(64, oldHash.length());
        assertFalse(oldRefresh.contains(oldHash));
        String body = refresh(oldRefresh, 200);
        var renewed = mapper.readTree(body);
        String newRefresh = renewed.get("refreshToken").asText();
        assertNotEquals(oldRefresh, newRefresh);
        assertNotEquals(oldHash, sessionRepository.findById(sessionId).orElseThrow().getRefreshTokenHash());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + renewed.get("accessToken").asText()))
                .andExpect(status().isOk());
        refresh(oldRefresh, 401);
    }

    @Test
    void refreshedAccessTokenCannotOutliveSession() throws Exception {
        var auth = mapper.readTree(register(email(), ""));
        long id = Long.parseLong(decoder.decode(auth.get("accessToken").asText()).getClaimAsString("sid"));
        var session = sessionRepository.findById(id).orElseThrow();
        Instant deadline = Instant.now().plusSeconds(120);
        session.setExpiresAt(deadline);
        sessionRepository.saveAndFlush(session);
        var refreshed = mapper.readTree(refresh(auth.get("refreshToken").asText(), 200));
        Jwt accessToken = decoder.decode(refreshed.get("accessToken").asText());
        assertFalse(accessToken.getExpiresAt().isAfter(deadline));
        assertTrue(refreshed.get("expiresIn").asLong() <= 120);
        assertTrue(refreshed.get("refreshExpiresIn").asLong() <= 120);
    }

    @Test
    void invalidMissingAndExpiredRefreshTokensAreRejected() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        refresh("not-a-refresh-token", 401);
        var auth = mapper.readTree(register(email(), ""));
        long id = Long.parseLong(decoder.decode(auth.get("accessToken").asText()).getClaimAsString("sid"));
        var session = sessionRepository.findById(id).orElseThrow();
        session.setExpiresAt(Instant.now().minusSeconds(1));
        sessionRepository.saveAndFlush(session);
        refresh(auth.get("refreshToken").asText(), 401);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesCurrentSessionAndLeavesOtherLoginActive() throws Exception {
        String email = email();
        var first = mapper.readTree(register(email, ""));
        String second = login(email, "password123");
        String firstToken = first.get("accessToken").asText();
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
        refresh(first.get("refreshToken").asText(), 401);
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void patchUpdatesOnlyAuthenticatedUsersEmailAndPreservesPasswordAndRole() throws Exception {
        String originalEmail = email();
        var auth = mapper.readTree(register(originalEmail, ""));
        String changedEmail = email();
        String token = auth.get("accessToken").asText();
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + changedEmail.toUpperCase() + "\",\"currentPassword\":\"password123\",\"role\":\"ADMIN\",\"id\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(changedEmail))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertTrue(passwords.matches("password123", users.findByEmailIgnoreCase(changedEmail).orElseThrow().getPasswordHash()));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(changedEmail));
        assertNotNull(login(changedEmail, "password123"));
    }

    @Test
    void passwordChangeRevokesAllSessionsAndRequiresLoginWithNewPassword() throws Exception {
        String email = email();
        var auth = mapper.readTree(register(email, ""));
        String original = auth.get("accessToken").asText();
        String anotherLogin = login(email, "password123");
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + original)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"new-password123\",\"currentPassword\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        for (String token : new String[] { original, anotherLogin }) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        assertNotNull(login(email, "new-password123"));
        refresh(auth.get("refreshToken").asText(), 401);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "password123"))).andExpect(status().isUnauthorized());
    }

    @Test
    void patchEmailAndPasswordTogetherWorks() throws Exception {
        var auth = mapper.readTree(register(email(), ""));
        String changedEmail = email();
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + auth.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + changedEmail + "\",\"password\":\"new-password123\",\"currentPassword\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(changedEmail));
        assertNotNull(login(changedEmail, "new-password123"));
    }

    @Test
    void patchRejectsInvalidInputMissingAuthenticationAndIncorrectCurrentPassword() throws Exception {
        mvc.perform(patch("/api/auth/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.com\"}")).andExpect(status().isUnauthorized());
        String token = mapper.readTree(register(email(), "")).get("accessToken").asText();
        for (String body : new String[] {
                "{}", "{\"currentPassword\":\"password123\"}",
                "{\"email\":\"invalid\",\"currentPassword\":\"password123\"}",
                "{\"email\":\"  \",\"currentPassword\":\"password123\"}",
                "{\"password\":\"short\",\"currentPassword\":\"password123\"}",
                "{\"password\":\"        \",\"currentPassword\":\"password123\"}",
                "{\"email\":\"other@example.com\"}" }) {
            mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.com\",\"currentPassword\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchRejectsAnotherUsersEmailButAllowsOwnEmail() throws Exception {
        String email = email();
        String token = mapper.readTree(register(email, "")).get("accessToken").asText();
        String otherEmail = email();
        register(otherEmail, "");
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"currentPassword\":\"password123\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + otherEmail.toUpperCase() + "\",\"currentPassword\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousRefreshRequestsCannotBothUseSameToken() throws Exception {
        var auth = mapper.readTree(register(email(), ""));
        long userId = auth.get("user").get("id").asLong();
        String refreshToken = auth.get("refreshToken").asText();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    sessions.refresh(refreshToken);
                    return true;
                } catch (InvalidRefreshTokenException exception) {
                    return false;
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            List<Boolean> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        } finally {
            users.deleteById(userId);
        }
    }

    private String refresh(String token, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("accessToken").asText();
    }

    private String register(String email, String extraFields) throws Exception {
        String body = credentials(email, "password123");
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body.substring(0, body.length() - 1) + extraFields + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String email() { return UUID.randomUUID() + "@example.com"; }

    @RestController
    static class RoleTestController {
        @GetMapping("/auth-tests/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String admin() { return "allowed"; }
    }
}
