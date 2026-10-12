package com.AgentOps.security;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer,
                            @Min(60) @Max(86400) long expirationSeconds) {
}
