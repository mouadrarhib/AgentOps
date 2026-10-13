package com.AgentOps.dto.response;

import com.AgentOps.entities.enums.TicketCategory;
import com.AgentOps.entities.enums.TicketStatus;
import com.AgentOps.entities.enums.TicketPriority;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long id;

    private Long orderId;

    private Long customerId;

    private TicketCategory category;

    private TicketStatus status;

    private TicketPriority priority;

    private Long assignedToId;
}
