package com.AgentOps.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security.refresh-token")
public record RefreshTokenProperties(@Min(3600) @Max(2592000) long expirationSeconds) {
}
