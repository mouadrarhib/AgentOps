package com.AgentOps;

import com.AgentOps.dto.request.CreateTicketRequest;
import com.AgentOps.dto.request.UpdateTicketRequest;
import com.AgentOps.entities.Order;
import com.AgentOps.entities.Ticket;
import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.*;
import com.AgentOps.exceptions.BadRequestException;
import com.AgentOps.exceptions.ForbiddenException;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.helpers.SecurityHelper;
import com.AgentOps.helpers.UserHelper;
import com.AgentOps.repositories.OrderRepository;
import com.AgentOps.repositories.TicketRepository;
import com.AgentOps.services.servicesImp.TicketServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTests {
    @Mock TicketRepository tickets;
    @Mock OrderRepository orders;
    @Mock SecurityHelper security;
    @Mock UserHelper users;
    @InjectMocks TicketServiceImpl service;

    @Test
    void creationDerivesCustomerFromAuthorizedOrderAndSetsDefaults() {
        authenticate(UserRole.CUSTOMER);
        when(orders.findByIdAndCustomer_Id(10L, 42L)).thenReturn(Optional.of(order()));
        when(tickets.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.createTicket(new CreateTicketRequest(10L, TicketCategory.DELIVERY));
        assertEquals(42L, result.getCustomerId());
        assertEquals(TicketStatus.OPEN, result.getStatus());
        assertEquals(TicketPriority.NORMAL, result.getPriority());
        assertNull(result.getAssignedToId());
        verify(orders, never()).findById(anyLong());
    }

    @Test
    void customerCannotCreateTicketForInvisibleOrder() {
        authenticate(UserRole.CUSTOMER);
        assertThrows(ResourceNotFoundException.class,
                () -> service.createTicket(new CreateTicketRequest(10L, TicketCategory.ORDER)));
        verifyNoInteractions(tickets);
    }

    @Test
    void customerDetailUsesOwnershipQuery() {
        authenticate(UserRole.CUSTOMER);
        when(tickets.findByIdAndCustomer_Id(20L, 42L)).thenReturn(Optional.of(ticket()));
        assertEquals(20L, service.getTicket(20L).getId());
        assertThrows(ResourceNotFoundException.class, () -> service.getTicket(21L));
        verify(tickets, never()).findById(anyLong());
    }

    @Test
    void customerCannotPatchEvenOwnTicket() {
        authenticate(UserRole.CUSTOMER);
        assertThrows(ForbiddenException.class,
                () -> service.updateTicket(20L, new UpdateTicketRequest(TicketStatus.CLOSED, null, null, null)));
        verifyNoInteractions(tickets, users);
    }

    @Test
    void partialPatchPreservesOtherFields() {
        authenticate(UserRole.SUPPORT);
        Ticket ticket = ticket();
        ticket.setAssignedTo(user(7L, UserRole.SUPPORT));
        when(tickets.findById(20L)).thenReturn(Optional.of(ticket));
        when(tickets.saveAndFlush(ticket)).thenReturn(ticket);
        var response = service.updateTicket(20L, new UpdateTicketRequest(null, TicketPriority.HIGH, null, null));
        assertEquals(TicketStatus.OPEN, response.getStatus());
        assertEquals(TicketPriority.HIGH, response.getPriority());
        assertEquals(7L, response.getAssignedToId());
    }

    @Test
    void invalidAssigneeDoesNotMutateTicket() {
        authenticate(UserRole.ADMIN);
        Ticket ticket = ticket();
        when(tickets.findById(20L)).thenReturn(Optional.of(ticket));
        when(users.getUserById(42L)).thenReturn(user(42L, UserRole.CUSTOMER));
        assertThrows(BadRequestException.class,
                () -> service.updateTicket(20L, new UpdateTicketRequest(TicketStatus.RESOLVED, null, 42L, null)));
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void staffCanAssignAndExplicitlyUnassign() {
        authenticate(UserRole.SUPPORT);
        Ticket ticket = ticket();
        when(tickets.findById(20L)).thenReturn(Optional.of(ticket));
        when(tickets.saveAndFlush(ticket)).thenReturn(ticket);
        when(users.getUserById(7L)).thenReturn(user(7L, UserRole.ADMIN));
        assertEquals(7L, service.updateTicket(20L, new UpdateTicketRequest(null, null, 7L, null)).getAssignedToId());
        assertNull(service.updateTicket(20L, new UpdateTicketRequest(null, null, null, true)).getAssignedToId());
    }

    @Test
    void staffCannotPatchMissingTicket() {
        authenticate(UserRole.ADMIN);
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateTicket(20L, new UpdateTicketRequest(TicketStatus.CLOSED, null, null, null)));
    }

    private void authenticate(UserRole role) {
        when(security.getCurrentUserId()).thenReturn(42L);
        when(security.hasRole(any(UserRole.class))).thenAnswer(invocation -> invocation.getArgument(0) == role);
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        return user;
    }

    private Order order() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setCustomer(user(42L, UserRole.CUSTOMER));
        return order;
    }

    private Ticket ticket() {
        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 20L);
        ticket.setOrder(order());
        ticket.setCustomer(ticket.getOrder().getCustomer());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCategory(TicketCategory.DELIVERY);
        return ticket;
    }
}
