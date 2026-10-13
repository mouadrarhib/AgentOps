package com.AgentOps.dto.request;

import com.AgentOps.entities.enums.TicketPriority;
import com.AgentOps.entities.enums.TicketStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

public record UpdateTicketRequest(TicketStatus status, TicketPriority priority,
                                  @Positive Long assignedToId, Boolean unassign) {
    @AssertTrue(message = "Provide status, priority, assignedToId, or unassign=true")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public boolean isUpdateProvided() {
        return status != null || priority != null || assignedToId != null || Boolean.TRUE.equals(unassign);
    }

    @AssertTrue(message = "Cannot assign and unassign a ticket in the same request")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public boolean isAssignmentConsistent() {
        return assignedToId == null || !Boolean.TRUE.equals(unassign);
    }
}
