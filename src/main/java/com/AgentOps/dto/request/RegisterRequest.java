package com.AgentOps.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
}
