package com.AgentOps.repositories;

import com.AgentOps.entities.Ticket;
import com.AgentOps.entities.enums.TicketCategory;
import com.AgentOps.entities.enums.TicketPriority;
import com.AgentOps.entities.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByIdAndCustomer_Id(Long id, Long customerId);

    @Query("""
            select ticket from Ticket ticket left join ticket.assignedTo assignee
            where (:staff = true or ticket.customer.id = :currentUserId)
              and (:status is null or ticket.status = :status)
              and (:priority is null or ticket.priority = :priority)
              and (:category is null or ticket.category = :category)
              and (:orderId is null or ticket.order.id = :orderId)
              and (:customerId is null or ticket.customer.id = :customerId)
              and (:assignedToId is null or assignee.id = :assignedToId)
              and (:unassigned is null
                   or (:unassigned = true and assignee.id is null)
                   or (:unassigned = false and assignee.id is not null))
            """)
    Page<Ticket> findVisibleTickets(@Param("currentUserId") Long currentUserId, @Param("staff") boolean staff,
            @Param("status") TicketStatus status, @Param("priority") TicketPriority priority,
            @Param("category") TicketCategory category, @Param("orderId") Long orderId,
            @Param("customerId") Long customerId, @Param("assignedToId") Long assignedToId,
            @Param("unassigned") Boolean unassigned, Pageable pageable);
}
