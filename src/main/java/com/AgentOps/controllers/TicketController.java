package com.AgentOps.controllers;

import com.AgentOps.dto.request.CreateTicketRequest;
import com.AgentOps.dto.request.TicketFilterRequest;
import com.AgentOps.dto.request.UpdateTicketRequest;
import com.AgentOps.dto.response.TicketPageResponse;
import com.AgentOps.dto.response.TicketResponse;
import com.AgentOps.services.TicketService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
@SecurityRequirement(name = "bearerAuth")
public class TicketController {
    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Valid @RequestBody CreateTicketRequest request, HttpServletResponse response) {
        TicketResponse ticket = tickets.createTicket(request);
        response.setHeader(HttpHeaders.LOCATION, "/tickets/" + ticket.getId());
        return ticket;
    }

    @GetMapping
    public TicketPageResponse list(@Valid @ModelAttribute TicketFilterRequest filters,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return tickets.listTickets(filters, page, size);
    }

    @GetMapping("/{ticketId}")
    public TicketResponse get(@PathVariable @Positive Long ticketId) {
        return tickets.getTicket(ticketId);
    }

    @PatchMapping("/{ticketId}")
    public TicketResponse update(@PathVariable @Positive Long ticketId, @Valid @RequestBody UpdateTicketRequest request) {
        return tickets.updateTicket(ticketId, request);
    }
}
