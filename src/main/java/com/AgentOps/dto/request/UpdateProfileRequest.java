package com.AgentOps.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Email @Size(max = 254) @Pattern(regexp = ".*\\S.*", message = "Email must not be blank") String email,
        @Size(min = 8, max = 128) @Pattern(regexp = "(?s).*\\S.*", message = "Password must not be blank")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @NotBlank @Size(max = 128)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword) {

    @AssertTrue(message = "Provide an email or a new password")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public boolean isUpdateProvided() {
        return email != null || password != null;
    }

    @Override
    public String toString() {
        return "UpdateProfileRequest[password=REDACTED, currentPassword=REDACTED]";
    }
}
