package com.AgentOps;

import com.AgentOps.entities.*;
import com.AgentOps.entities.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ModelPersistenceTests {
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsRelatedEntitiesAndStringEnums() {
        User customer = new User();
        customer.setEmail(UUID.randomUUID() + "@example.test");
        customer.setPasswordHash("test-hash");
        customer.setRole(UserRole.CUSTOMER);
        entityManager.persist(customer);

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        entityManager.persist(order);

        Ticket ticket = new Ticket();
        ticket.setOrder(order);
        ticket.setCustomer(customer);
        ticket.setCategory(TicketCategory.DELIVERY);
        ticket.setStatus(TicketStatus.OPEN);
        entityManager.persist(ticket);

        Conversation conversation = new Conversation();
        conversation.setUser(customer);
        entityManager.persist(conversation);

        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(MessageRole.USER);
        message.setContent("Where is my order?");
        entityManager.persist(message);

        ToolAudit audit = new ToolAudit();
        audit.setUser(customer);
        audit.setToolName("lookup_order");
        audit.setArgumentsSummary("order_id=" + order.getId());
        audit.setResultStatus(ToolResultStatus.SUCCESS);
        entityManager.persist(audit);

        Approval approval = new Approval();
        approval.setRequestedBy(customer);
        approval.setActionType(ApprovalActionType.CANCEL_ORDER);
        approval.setTargetId(order.getId());
        approval.setStatus(ApprovalStatus.PENDING);
        entityManager.persist(approval);
        entityManager.flush();
        entityManager.clear();

        Ticket storedTicket = entityManager.find(Ticket.class, ticket.getId());
        assertEquals(customer.getId(), storedTicket.getCustomer().getId());
        assertEquals(order.getId(), storedTicket.getOrder().getId());
        assertEquals(customer.getId(), storedTicket.getOrder().getCustomer().getId());
        Message storedMessage = entityManager.find(Message.class, message.getId());
        assertEquals(customer.getId(), storedMessage.getConversation().getUser().getId());
        assertEquals(MessageRole.USER, storedMessage.getRole());
        assertNotNull(storedMessage.getCreatedAt());
        Order storedOrder = entityManager.find(Order.class, order.getId());
        assertNotNull(storedOrder.getCreatedAt());
        assertNotNull(storedOrder.getUpdatedAt());
        assertEquals(customer.getId(), entityManager.find(ToolAudit.class, audit.getId()).getUser().getId());
        Approval storedApproval = entityManager.find(Approval.class, approval.getId());
        assertEquals(customer.getId(), storedApproval.getRequestedBy().getId());
        assertNull(storedApproval.getApprovedBy());
        assertNull(storedApproval.getDecidedAt());
        assertNotNull(storedApproval.getCreatedAt());
        assertEquals("PENDING", entityManager.createNativeQuery(
                "select status from orders where id = :id", String.class)
                .setParameter("id", order.getId()).getSingleResult());
        assertEquals("CUSTOMER", entityManager.createNativeQuery(
                "select role from users where id = :id", String.class)
                .setParameter("id", customer.getId()).getSingleResult());
    }
}
