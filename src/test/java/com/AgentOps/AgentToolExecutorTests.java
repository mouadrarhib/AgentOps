package com.AgentOps;

import com.AgentOps.agent.*;
import com.AgentOps.dto.response.OrderResponse;
import com.AgentOps.entities.enums.*;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.services.OrderService;
import com.AgentOps.services.TicketService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentToolExecutorTests {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    @Mock OrderService orders;
    @Mock TicketService tickets;
    @Mock ToolAuditService audits;
    AgentToolExecutor executor;

    @BeforeEach
    void setup() {
        executor = new AgentToolExecutor(orders, tickets, VALIDATORS.getValidator(), JsonMapper.builder().build(), audits);
    }

    @AfterAll
    static void closeValidators() { VALIDATORS.close(); }

    @Test
    void onlyAllowlistedToolsCanRun() {
        var result = executor.execute(42L, call("run_sql", "{}"));
        assertEquals(ToolResultStatus.DENIED, result.status());
        verifyNoInteractions(orders, tickets);
        verify(audits).record(eq(42L), eq("run_sql"), anyString(), eq(ToolResultStatus.DENIED));
    }

    @Test
    void strictArgumentTypesAndUnknownFieldsAreRejectedBeforeBackendAccess() {
        for (String json : new String[] { "{", "[]", "{}", "{\"orderId\":0}", "{\"orderId\":1.5}",
                "{\"orderId\":\"1\"}", "{\"orderId\":null}", "{\"orderId\":999999999999999999999999}",
                "{\"orderId\":1,\"sql\":\"provider-debug-secret\"}" }) {
            assertEquals(ToolResultStatus.FAILURE, executor.execute(42L, call("get_order", json)).status());
        }
        verifyNoInteractions(orders, tickets);
        verify(audits, times(9)).record(42L, "get_order", "Arguments rejected.", ToolResultStatus.FAILURE);
    }

    @Test
    void dtoValidationRejectsConflictingAssignments() {
        var result = executor.execute(42L, call("update_ticket", "{\"ticketId\":1,\"assignedToId\":7,\"unassign\":true}"));
        assertEquals(ToolResultStatus.FAILURE, result.status());
        verifyNoInteractions(tickets);
    }

    @Test
    void backendOwnershipFailuresAreDeniedAndAudited() {
        when(orders.getOrder(10L)).thenThrow(new ResourceNotFoundException("Order", 10L));
        var result = executor.execute(42L, call("get_order", "{\"orderId\":10}"));
        assertEquals(ToolResultStatus.DENIED, result.status());
        assertTrue(result.content().contains("404"));
        verify(audits).record(42L, "get_order", "{\"orderId\":10}", ToolResultStatus.DENIED);
    }

    @Test
    void successfulToolsUseExistingServiceAndRecordAudit() {
        when(orders.getOrder(10L)).thenReturn(new OrderResponse(10L, 42L, OrderStatus.PENDING, null, null));
        assertEquals(ToolResultStatus.SUCCESS, executor.execute(42L, call("get_order", "{\"orderId\":10}")).status());
        verify(audits).record(42L, "get_order", "{\"orderId\":10}", ToolResultStatus.SUCCESS);
    }

    @Test
    void invalidEnumsAndOversizedPagesAreRejected() {
        assertEquals(ToolResultStatus.FAILURE, executor.execute(42L, call("list_orders", "{\"size\":100}")).status());
        assertEquals(ToolResultStatus.FAILURE, executor.execute(42L, call("create_ticket", "{\"orderId\":1,\"category\":\"INVALID\"}")).status());
        verifyNoInteractions(orders, tickets);
    }

    @Test
    void customerToolListHidesStaffUpdateTool() {
        assertFalse(executor.definitions(false).toString().contains("update_ticket"));
        assertTrue(executor.definitions(true).toString().contains("update_ticket"));
    }

    @Test
    void missingProviderKeyReturnsServiceUnavailableWithoutNetworkCall() {
        var properties = new DeepSeekProperties("", URI.create("https://api.deepseek.com"), "deepseek-flash", 30, 4, 8, 40, 2048);
        var client = new DeepSeekClient(properties, JsonMapper.builder().build());
        var failure = assertThrows(com.AgentOps.exceptions.AiProviderException.class, client::requireConfigured);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatus());
    }

    private DeepSeekClient.ToolCall call(String name, String arguments) { return new DeepSeekClient.ToolCall("call-1", name, arguments); }
}
