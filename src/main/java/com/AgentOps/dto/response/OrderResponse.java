package com.AgentOps.dto.response;

import com.AgentOps.entities.enums.OrderStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;

    private Long customerId;

    private OrderStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
