package com.AgentOps.dto.request;

import com.AgentOps.entities.enums.TicketCategory;
import com.AgentOps.entities.enums.TicketPriority;
import com.AgentOps.entities.enums.TicketStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

public record TicketFilterRequest(TicketStatus status, TicketPriority priority, TicketCategory category,
                                  @Positive Long orderId, @Positive Long customerId,
                                  @Positive Long assignedToId, Boolean unassigned) {
    @AssertTrue(message = "Cannot filter by an assignee and unassigned=true together")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public boolean isAssignmentConsistent() {
        return assignedToId == null || !Boolean.TRUE.equals(unassigned);
    }
}
