package com.AgentOps.dto.request;

import com.AgentOps.entities.enums.TicketCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTicketRequest(
        @NotNull @Positive Long orderId,
        @NotNull TicketCategory category) {
}
