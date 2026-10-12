package com.AgentOps.dto.response;

import com.AgentOps.entities.enums.ApprovalActionType;
import com.AgentOps.entities.enums.ApprovalStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResponse {
    private Long id;

    private Long requestedById;

    private ApprovalActionType actionType;

    private Long targetId;

    private ApprovalStatus status;

    private Long approvedById;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant decidedAt;
}
