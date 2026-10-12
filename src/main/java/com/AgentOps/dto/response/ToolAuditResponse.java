package com.AgentOps.dto.response;

import com.AgentOps.entities.enums.ToolResultStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ToolAuditResponse {
    private Long id;

    private Long userId;

    private String toolName;

    private String argumentsSummary;

    private ToolResultStatus resultStatus;

    private Instant createdAt;
}
