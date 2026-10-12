package com.AgentOps.security;

import com.AgentOps.entities.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String generateToken(User user, Long sessionId, Instant sessionExpiresAt) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.expirationSeconds());
        if (sessionExpiresAt.isBefore(expiresAt)) {
            expiresAt = sessionExpiresAt;
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).subject(user.getId().toString())
                .issuedAt(now).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).claim("role", user.getRole().name())
                .claim("sid", sessionId.toString()).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    public long expirationSeconds() {
        return properties.expirationSeconds();
    }
}
