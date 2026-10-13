package com.AgentOps.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.AgentOps.entities.enums.TicketCategory;
import com.AgentOps.entities.enums.TicketStatus;
import com.AgentOps.entities.enums.TicketPriority;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_customer_id", columnList = "customer_id"),
        @Index(name = "idx_tickets_order_id", columnList = "order_id"),
        @Index(name = "idx_tickets_assigned_to", columnList = "assigned_to_id")
})
public class Ticket extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 32)
    @ColumnDefault("'NORMAL'")
    private TicketPriority priority = TicketPriority.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private long version;
}
