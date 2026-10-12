package com.AgentOps.dto.request;

import com.AgentOps.entities.enums.ApprovalActionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {
    @NotNull(message = "requestedById is required")
    @Positive(message = "requestedById must be positive")
    private Long requestedById;

    @NotNull(message = "actionType is required")
    private ApprovalActionType actionType;

    @NotNull(message = "targetId is required")
    @Positive(message = "targetId must be positive")
    private Long targetId;
}
