package com.AgentOps.dto.request;

import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @Positive(message = "customerId must be positive") Long customerId) {
}
