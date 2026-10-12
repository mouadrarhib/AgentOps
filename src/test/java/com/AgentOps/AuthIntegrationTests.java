package com.AgentOps;

import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.repositories.UserRepository;
import com.AgentOps.security.JwtProperties;
import com.AgentOps.security.JwtService;
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
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
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
    @Autowired JwtService tokens;

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
        for (String kind : new String[] { "expired", "issuer", "expiration", "role", "subject" }) {
            JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                    .issuer(kind.equals("issuer") ? "other-app" : properties.issuer())
                    .subject(kind.equals("subject") ? "invalid" : "1")
                    .issuedAt(now.minusSeconds(600))
                    .claim("role", kind.equals("role") ? "SUPERUSER" : "CUSTOMER");
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
        mvc.perform(get("/auth-tests/admin").header("Authorization", "Bearer " + tokens.generateToken(admin)))
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
