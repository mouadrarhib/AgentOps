package com.AgentOps.dto.request;

import com.AgentOps.entities.enums.ToolResultStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ToolAuditRequest {
    @NotNull(message = "userId is required")
    @Positive(message = "userId must be positive")
    private Long userId;

    @NotBlank(message = "Tool name is required")
    @Size(max = 255, message = "Tool name must not exceed 255 characters")
    private String toolName;

    @NotBlank(message = "Arguments summary is required")
    private String argumentsSummary;

    @NotNull(message = "resultStatus is required")
    private ToolResultStatus resultStatus;
}
