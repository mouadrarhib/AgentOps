package com.AgentOps.repositories;

import com.AgentOps.entities.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByCustomer_Id(Long customerId, Pageable pageable);
    Optional<Order> findByIdAndCustomer_Id(Long id, Long customerId);
}
