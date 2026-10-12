package com.AgentOps.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank @Size(max = 128)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String refreshToken) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=REDACTED]";
    }
}
