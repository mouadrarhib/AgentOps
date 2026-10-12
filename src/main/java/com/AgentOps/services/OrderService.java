package com.AgentOps.services;

import com.AgentOps.dto.request.CreateOrderRequest;
import com.AgentOps.dto.response.OrderPageResponse;
import com.AgentOps.dto.response.OrderResponse;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
    OrderPageResponse listOrders(int page, int size);
    OrderResponse getOrder(Long orderId);
}
