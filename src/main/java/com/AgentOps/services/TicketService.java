package com.AgentOps.services;

import com.AgentOps.dto.request.CreateTicketRequest;
import com.AgentOps.dto.request.TicketFilterRequest;
import com.AgentOps.dto.request.UpdateTicketRequest;
import com.AgentOps.dto.response.TicketPageResponse;
import com.AgentOps.dto.response.TicketResponse;

public interface TicketService {
    TicketResponse createTicket(CreateTicketRequest request);
    TicketPageResponse listTickets(TicketFilterRequest filters, int page, int size);
    TicketResponse getTicket(Long ticketId);
    TicketResponse updateTicket(Long ticketId, UpdateTicketRequest request);
}
