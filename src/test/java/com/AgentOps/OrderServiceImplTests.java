package com.AgentOps;

import com.AgentOps.dto.request.CreateOrderRequest;
import com.AgentOps.entities.Order;
import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.OrderStatus;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.exceptions.BadRequestException;
import com.AgentOps.exceptions.ForbiddenException;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.helpers.SecurityHelper;
import com.AgentOps.helpers.UserHelper;
import com.AgentOps.repositories.OrderRepository;
import com.AgentOps.services.servicesImp.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTests {
    @Mock OrderRepository orders;
    @Mock SecurityHelper security;
    @Mock UserHelper users;
    @InjectMocks OrderServiceImpl service;

    @Test
    void customerCreationAssignsCurrentUserAndPendingStatus() {
        User customer = user(42L, UserRole.CUSTOMER);
        when(security.getCurrentUser()).thenReturn(customer);
        when(orders.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.createOrder(new CreateOrderRequest(null));
        assertEquals(42L, response.getCustomerId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        verifyNoInteractions(users);
    }

    @Test
    void customerCannotCreateOrderForAnotherUser() {
        when(security.getCurrentUser()).thenReturn(user(42L, UserRole.CUSTOMER));
        assertThrows(ForbiddenException.class, () -> service.createOrder(new CreateOrderRequest(43L)));
        verifyNoInteractions(orders, users);
    }

    @Test
    void staffCanCreateOrderForCustomer() {
        when(security.getCurrentUser()).thenReturn(user(1L, UserRole.ADMIN));
        when(security.hasRole(UserRole.ADMIN)).thenReturn(true);
        when(users.getUserById(42L)).thenReturn(user(42L, UserRole.CUSTOMER));
        when(orders.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(42L, service.createOrder(new CreateOrderRequest(42L)).getCustomerId());
    }

    @Test
    void staffMustSelectAnActualCustomer() {
        when(security.getCurrentUser()).thenReturn(user(1L, UserRole.ADMIN));
        when(security.hasRole(UserRole.ADMIN)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.createOrder(new CreateOrderRequest(null)));
        when(users.getUserById(2L)).thenReturn(user(2L, UserRole.SUPPORT));
        assertThrows(BadRequestException.class, () -> service.createOrder(new CreateOrderRequest(2L)));
        verifyNoInteractions(orders);
    }

    @Test
    void customerListingUsesDatabaseOwnershipFilter() {
        when(security.getCurrentUserId()).thenReturn(42L);
        when(orders.findByCustomer_Id(eq(42L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order(99L, 42L))));
        var page = service.listOrders(0, 20);
        assertEquals(1, page.totalElements());
        assertEquals(99L, page.content().getFirst().getId());
        verify(orders, never()).findAll(any(Pageable.class));
    }

    @Test
    void supportListingCanQueryAllOrders() {
        when(security.getCurrentUserId()).thenReturn(1L);
        when(security.hasRole(UserRole.ADMIN)).thenReturn(false);
        when(security.hasRole(UserRole.SUPPORT)).thenReturn(true);
        when(orders.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertTrue(service.listOrders(0, 20).content().isEmpty());
        verify(orders, never()).findByCustomer_Id(anyLong(), any(Pageable.class));
    }

    @Test
    void customerDetailScopesQueryAndHidesInaccessibleOrder() {
        when(security.getCurrentUserId()).thenReturn(42L);
        when(orders.findByIdAndCustomer_Id(99L, 42L)).thenReturn(Optional.of(order(99L, 42L)));
        assertEquals(99L, service.getOrder(99L).getId());
        assertThrows(ResourceNotFoundException.class, () -> service.getOrder(100L));
        verify(orders, never()).findById(anyLong());
    }

    @Test
    void invalidPaginationAndOrderIdsFailBeforeDatabaseAccess() {
        assertThrows(BadRequestException.class, () -> service.listOrders(-1, 20));
        assertThrows(BadRequestException.class, () -> service.listOrders(0, 0));
        assertThrows(BadRequestException.class, () -> service.listOrders(0, 101));
        assertThrows(BadRequestException.class, () -> service.getOrder(0L));
        assertThrows(BadRequestException.class, () -> service.getOrder(null));
        verifyNoInteractions(orders, security, users);
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        return user;
    }

    private Order order(Long id, Long customerId) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setCustomer(user(customerId, UserRole.CUSTOMER));
        order.setStatus(OrderStatus.PENDING);
        return order;
    }
}
