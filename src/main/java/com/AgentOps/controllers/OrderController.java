package com.AgentOps.controllers;

import com.AgentOps.dto.request.CreateOrderRequest;
import com.AgentOps.dto.response.OrderPageResponse;
import com.AgentOps.dto.response.OrderResponse;
import com.AgentOps.services.OrderService;
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
@RequestMapping("/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request, HttpServletResponse response) {
        OrderResponse order = orders.createOrder(request);
        response.setHeader(HttpHeaders.LOCATION, "/orders/" + order.getId());
        return order;
    }

    @GetMapping
    public OrderPageResponse list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.listOrders(page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable @Positive Long orderId) {
        return orders.getOrder(orderId);
    }
}
