package com.AgentOps.agent;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("agent.deepseek")
public record DeepSeekProperties(String apiKey, @NotNull URI baseUrl, @NotBlank String model,
        @Min(5) @Max(120) int timeoutSeconds, @Min(1) @Max(8) int maxToolRounds,
        @Min(1) @Max(16) int maxToolCalls, @Min(2) @Max(100) int contextMessages,
        @Min(256) @Max(8192) int maxOutputTokens) {
    public long turnLeaseSeconds() {
        return (long) timeoutSeconds * (maxToolRounds + 1) + 60;
    }

    @Override
    public String toString() { return "DeepSeekProperties[model=" + model + ", apiKey=REDACTED]"; }
}
