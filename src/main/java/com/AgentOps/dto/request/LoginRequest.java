package com.AgentOps.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
}
