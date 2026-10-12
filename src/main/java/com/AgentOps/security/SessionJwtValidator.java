package com.AgentOps.security;

import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.repositories.AuthSessionRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SessionJwtValidator implements OAuth2TokenValidator<Jwt> {
    private final AuthSessionRepository sessions;

    public SessionJwtValidator(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            Object sessionClaim = jwt.getClaims().get("sid");
            Object roleClaim = jwt.getClaims().get("role");
            if (sessionClaim instanceof String sessionId && roleClaim instanceof String role) {
                long id = Long.parseLong(sessionId);
                long userId = Long.parseLong(jwt.getSubject());
                if (id > 0 && userId > 0 && sessions.existsByIdAndUser_IdAndUser_RoleAndRevokedFalseAndExpiresAtAfter(
                        id, userId, UserRole.valueOf(role), Instant.now())) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
        } catch (IllegalArgumentException exception) {
            // Malformed signed claims are authentication failures, never application errors.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "The authentication session is no longer valid.", null));
    }
}
