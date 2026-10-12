package com.AgentOps.services.servicesImp;

import com.AgentOps.dto.request.CreateOrderRequest;
import com.AgentOps.dto.response.OrderPageResponse;
import com.AgentOps.dto.response.OrderResponse;
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
import com.AgentOps.services.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orders;
    private final SecurityHelper security;
    private final UserHelper users;

    public OrderServiceImpl(OrderRepository orders, SecurityHelper security, UserHelper users) {
        this.orders = orders;
        this.security = security;
        this.users = users;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        User currentUser = security.getCurrentUser();
        boolean staff = isStaff();
        Long customerId = request.customerId();
        if (!staff && customerId != null && !customerId.equals(currentUser.getId())) {
            throw new ForbiddenException("You can only create orders for yourself.");
        }
        if (staff && customerId == null) {
            throw new BadRequestException("customerId is required when creating an order for a customer.");
        }
        User customer = customerId == null || customerId.equals(currentUser.getId())
                ? currentUser : users.getUserById(customerId);
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new BadRequestException("Orders must belong to a user with the CUSTOMER role.");
        }
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        return response(orders.saveAndFlush(order));
    }

    @Override
    public OrderPageResponse listOrders(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Page must be non-negative and size must be between 1 and 100.");
        }
        Long userId = security.getCurrentUserId();
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Order> result = isStaff() ? orders.findAll(pageable) : orders.findByCustomer_Id(userId, pageable);
        return new OrderPageResponse(result.getContent().stream().map(this::response).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public OrderResponse getOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BadRequestException("Order ID must be positive.");
        }
        Long userId = security.getCurrentUserId();
        // Scope the query before loading data; inaccessible and missing orders both return 404.
        Order order = (isStaff() ? orders.findById(orderId) : orders.findByIdAndCustomer_Id(orderId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return response(order);
    }

    private boolean isStaff() {
        return security.hasRole(UserRole.ADMIN) || security.hasRole(UserRole.SUPPORT);
    }

    private OrderResponse response(Order order) {
        return new OrderResponse(order.getId(), order.getCustomer().getId(), order.getStatus(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
