package com.AgentOps.services.servicesImp;

import com.AgentOps.dto.request.CreateTicketRequest;
import com.AgentOps.dto.request.TicketFilterRequest;
import com.AgentOps.dto.request.UpdateTicketRequest;
import com.AgentOps.dto.response.TicketPageResponse;
import com.AgentOps.dto.response.TicketResponse;
import com.AgentOps.entities.Order;
import com.AgentOps.entities.Ticket;
import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.TicketPriority;
import com.AgentOps.entities.enums.TicketStatus;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.exceptions.BadRequestException;
import com.AgentOps.exceptions.ForbiddenException;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.helpers.SecurityHelper;
import com.AgentOps.helpers.UserHelper;
import com.AgentOps.repositories.OrderRepository;
import com.AgentOps.repositories.TicketRepository;
import com.AgentOps.services.TicketService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {
    private final TicketRepository tickets;
    private final OrderRepository orders;
    private final SecurityHelper security;
    private final UserHelper users;

    public TicketServiceImpl(TicketRepository tickets, OrderRepository orders, SecurityHelper security, UserHelper users) {
        this.tickets = tickets;
        this.orders = orders;
        this.security = security;
        this.users = users;
    }

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Long userId = security.getCurrentUserId();
        Order order = (isStaff() ? orders.findById(request.orderId())
                : orders.findByIdAndCustomer_Id(request.orderId(), userId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));
        if (order.getCustomer().getRole() != UserRole.CUSTOMER) {
            throw new BadRequestException("Tickets must belong to a customer order.");
        }
        Ticket ticket = new Ticket();
        ticket.setOrder(order);
        ticket.setCustomer(order.getCustomer());
        ticket.setCategory(request.category());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.NORMAL);
        return response(tickets.saveAndFlush(ticket));
    }

    @Override
    public TicketPageResponse listTickets(TicketFilterRequest filters, int page, int size) {
        if (!filters.isAssignmentConsistent()) {
            throw new BadRequestException("Cannot filter by an assignee and unassigned=true together.");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Page must be non-negative and size must be between 1 and 100.");
        }
        Page<Ticket> result = tickets.findVisibleTickets(security.getCurrentUserId(), isStaff(), filters.status(),
                filters.priority(), filters.category(), filters.orderId(), filters.customerId(),
                filters.assignedToId(), filters.unassigned(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new TicketPageResponse(result.getContent().stream().map(this::response).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public TicketResponse getTicket(Long ticketId) {
        requirePositiveId(ticketId);
        Long userId = security.getCurrentUserId();
        Ticket ticket = (isStaff() ? tickets.findById(ticketId) : tickets.findByIdAndCustomer_Id(ticketId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        return response(ticket);
    }

    @Override
    @Transactional
    public TicketResponse updateTicket(Long ticketId, UpdateTicketRequest request) {
        requirePositiveId(ticketId);
        security.getCurrentUserId();
        if (!isStaff()) {
            throw new ForbiddenException("Only SUPPORT and ADMIN can update tickets.");
        }
        if (!request.isUpdateProvided() || !request.isAssignmentConsistent()) {
            throw new BadRequestException("Provide at least one update and do not assign and unassign together.");
        }
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        User assignee = null;
        if (request.assignedToId() != null) {
            assignee = users.getUserById(request.assignedToId());
            if (assignee.getRole() != UserRole.SUPPORT && assignee.getRole() != UserRole.ADMIN) {
                throw new BadRequestException("Tickets can only be assigned to SUPPORT or ADMIN accounts.");
            }
        }
        if (request.status() != null) {
            ticket.setStatus(request.status());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (assignee != null || Boolean.TRUE.equals(request.unassign())) {
            ticket.setAssignedTo(assignee);
        }
        return response(tickets.saveAndFlush(ticket));
    }

    private boolean isStaff() {
        return security.hasRole(UserRole.ADMIN) || security.hasRole(UserRole.SUPPORT);
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("Ticket ID must be positive.");
        }
    }

    private TicketResponse response(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getOrder().getId(), ticket.getCustomer().getId(),
                ticket.getCategory(), ticket.getStatus(), ticket.getPriority(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId());
    }
}
