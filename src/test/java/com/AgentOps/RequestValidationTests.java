package com.AgentOps;

import com.AgentOps.dto.request.*;
import com.AgentOps.entities.enums.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequestValidationTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        FACTORY.close();
    }

    @Test
    void rejectsEmptyRequestsForEveryEntity() {
        List<Object> requests = List.of(new UserRequest(), new OrderRequest(),
                new TicketRequest(), new ConversationRequest(), new MessageRequest(),
                new ToolAuditRequest(), new ApprovalRequest());

        for (Object request : requests) {
            assertFalse(VALIDATOR.validate(request).isEmpty(), request.getClass().getSimpleName());
        }
    }

    @Test
    void acceptsValidRequestsForEveryEntity() {
        List<Object> requests = List.of(
                new UserRequest("customer@example.test", "valid-password", UserRole.CUSTOMER),
                new OrderRequest(1L, OrderStatus.PENDING),
                new TicketRequest(1L, 2L, TicketCategory.DELIVERY, TicketStatus.OPEN),
                new ConversationRequest(1L),
                new MessageRequest(1L, MessageRole.USER, "Where is my order?"),
                new ToolAuditRequest(1L, "lookup_order", "order_id=1", ToolResultStatus.SUCCESS),
                new ApprovalRequest(1L, ApprovalActionType.CANCEL_ORDER, 2L));

        for (Object request : requests) {
            assertTrue(VALIDATOR.validate(request).isEmpty(), request.getClass().getSimpleName());
        }
    }

    @Test
    void rejectsMalformedEmailWeakPasswordAndOverlongFields() {
        UserRequest user = new UserRequest("invalid-email", "short", UserRole.CUSTOMER);
        assertEquals(2, VALIDATOR.validate(user).size());
        user.setEmail("a".repeat(250) + "@example.test");
        user.setPassword("a".repeat(129));
        assertFalse(VALIDATOR.validateProperty(user, "email").isEmpty());
        assertFalse(VALIDATOR.validateProperty(user, "password").isEmpty());
        ToolAuditRequest audit = new ToolAuditRequest(1L, "a".repeat(256), "{}", ToolResultStatus.SUCCESS);
        assertFalse(VALIDATOR.validateProperty(audit, "toolName").isEmpty());
    }

    @Test
    void rejectsNonPositiveReferencesMissingEnumsAndBlankText() {
        OrderRequest order = new OrderRequest(0L, null);
        assertEquals(2, VALIDATOR.validate(order).size());
        TicketRequest ticket = new TicketRequest(-1L, 0L, null, null);
        assertEquals(4, VALIDATOR.validate(ticket).size());
        assertFalse(VALIDATOR.validate(new ConversationRequest(-1L)).isEmpty());
        assertFalse(VALIDATOR.validate(new MessageRequest(1L, MessageRole.USER, "  ")).isEmpty());
        assertFalse(VALIDATOR.validate(new ToolAuditRequest(1L, " ", " ", ToolResultStatus.SUCCESS)).isEmpty());
        ApprovalRequest approval = new ApprovalRequest(0L, null, -1L);
        assertEquals(3, VALIDATOR.validate(approval).size());
    }
}
