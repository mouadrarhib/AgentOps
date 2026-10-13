package com.AgentOps;

import com.AgentOps.agent.ConversationStore;
import com.AgentOps.dto.request.AgentChatRequest;
import com.AgentOps.entities.*;
import com.AgentOps.entities.enums.*;
import com.AgentOps.repositories.*;
import com.AgentOps.services.AuthSessionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentIntegrationTests {
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Queue<StubResponse> OUTPUTS = new ConcurrentLinkedQueue<>();
    private static final List<JsonNode> INPUTS = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> AUTH_HEADERS = Collections.synchronizedList(new ArrayList<>());
    private static final HttpServer SERVER = server();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired TicketRepository tickets;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired ToolAuditRepository audits;
    @Autowired AuthSessionService sessions;
    @Autowired ConversationStore store;

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("agent.deepseek.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("agent.deepseek.api-key", () -> "test-deepseek-key");
        registry.add("agent.deepseek.max-tool-rounds", () -> 2);
        registry.add("agent.deepseek.max-tool-calls", () -> 3);
    }

    @BeforeEach
    void clearProvider() { OUTPUTS.clear(); INPUTS.clear(); AUTH_HEADERS.clear(); }

    @AfterAll
    static void stopProvider() { SERVER.stop(0); }

    @Test
    void chatPersistsHistoryAndContinuesOwnedConversation() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        answer("Hello from DeepSeek.");
        var first = chat(owner, null, "Hello", 200);
        long id = first.get("conversationId").asLong();
        assertEquals("Hello from DeepSeek.", first.get("reply").get("content").asText());
        assertEquals("Bearer test-deepseek-key", AUTH_HEADERS.getFirst());
        assertEquals("disabled", INPUTS.getFirst().get("thinking").get("type").asText());
        answer("How can I help?");
        chat(owner, id, "Help me with my order", 200);
        assertEquals(4, INPUTS.get(1).get("messages").size());
        assertEquals("Hello from DeepSeek.", INPUTS.get(1).get("messages").get(2).get("content").asText());
        mvc.perform(get("/agent/conversations/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages", hasSize(4)))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));
        mvc.perform(get("/agent/conversations/" + id + "?page=1&size=1").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].content").value("Hello from DeepSeek."))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void conversationPrivacyAppliesEvenToAdmin() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Account admin = account(UserRole.ADMIN);
        answer("Private reply");
        long id = chat(owner, null, "Private question", 200).get("conversationId").asLong();
        mvc.perform(get("/agent/conversations/" + id).header("Authorization", bearer(admin))).andExpect(status().isNotFound());
        chat(admin, id, "Read the private messages", 404);
        mvc.perform(get("/agent/conversations").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/agent/conversations?size=1").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        assertEquals(1, INPUTS.size());
    }

    @Test
    void backendToolResultIsSentToProviderAndAudited() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Order order = order(owner);
        calls(tool("call-1", "get_order", "{\"orderId\":" + order.getId() + "}"));
        answer("Your order is pending.");
        var result = chat(owner, null, "Where is order " + order.getId() + "?", 200);
        assertEquals(1, result.get("toolCalls").asInt());
        JsonNode history = INPUTS.get(1).get("messages");
        JsonNode toolMessage = history.get(history.size() - 1);
        assertEquals("tool", toolMessage.get("role").asText());
        assertEquals("call-1", toolMessage.get("tool_call_id").asText());
        assertEquals("PENDING", mapper.readTree(toolMessage.get("content").asText()).get("data").get("status").asText());
        assertEquals(ToolResultStatus.SUCCESS, audits.findAll().getFirst().getResultStatus());
        assertEquals(owner.user().getId(), audits.findAll().getFirst().getUser().getId());
    }

    @Test
    void deniedToolsCannotReadOtherCustomersOrdersOrUpdateTickets() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Account other = account(UserRole.CUSTOMER);
        Order privateOrder = order(other);
        calls(tool("call-1", "get_order", "{\"orderId\":" + privateOrder.getId() + "}"),
                tool("call-2", "update_ticket", "{\"ticketId\":1,\"status\":\"CLOSED\"}"));
        answer("Those actions are not available.");
        chat(owner, null, "Try accessing another account", 200);
        assertTrue(audits.findAll().stream().allMatch(audit -> audit.getResultStatus() == ToolResultStatus.DENIED));
        JsonNode history = INPUTS.get(1).get("messages");
        assertEquals(404, mapper.readTree(history.get(history.size() - 2).get("content").asText()).get("error").get("status").asInt());
        assertEquals(403, mapper.readTree(history.get(history.size() - 1).get("content").asText()).get("error").get("status").asInt());
        assertFalse(INPUTS.getFirst().get("tools").toString().contains("update_ticket"));
    }

    @Test
    void duplicateWriteCallsCreateOnlyOneTicket() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Order order = order(owner);
        long initial = tickets.count();
        calls(tool("call-1", "create_ticket", "{\"orderId\":" + order.getId() + ",\"category\":\"DELIVERY\"}"),
                tool("call-2", "create_ticket", "{\"category\":\"DELIVERY\",\"orderId\":" + order.getId() + "}"));
        answer("Your ticket has been created.");
        chat(owner, null, "Create a delivery ticket for order " + order.getId(), 200);
        assertEquals(initial + 1, tickets.count());
        assertEquals(1, audits.count());
        assertEquals(TicketPriority.NORMAL, tickets.findAll().getFirst().getPriority());
    }

    @Test
    void staffToolCanUpdateTicketThroughExistingService() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Account staff = account(UserRole.SUPPORT);
        Ticket ticket = new Ticket();
        ticket.setOrder(order(owner));
        ticket.setCustomer(owner.user());
        ticket.setCategory(TicketCategory.ORDER);
        ticket.setStatus(TicketStatus.OPEN);
        tickets.saveAndFlush(ticket);
        calls(tool("call-1", "update_ticket", "{\"ticketId\":" + ticket.getId() + ",\"priority\":\"HIGH\"}"));
        answer("The priority is now high.");
        chat(staff, null, "Set this ticket's priority to HIGH", 200);
        assertEquals(TicketPriority.HIGH, tickets.findById(ticket.getId()).orElseThrow().getPriority());
        assertTrue(INPUTS.getFirst().get("tools").toString().contains("update_ticket"));
    }

    @Test
    void busyConversationRejectsOverlappingTurnAndStaleLeaseCanRecover() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        var active = store.begin(owner.user().getId(), new AgentChatRequest(null, "First request"));
        chat(owner, active.conversationId(), "Overlapping request", 409);
        assertTrue(INPUTS.isEmpty());
        var conversation = conversations.findById(active.conversationId()).orElseThrow();
        conversation.setProcessingStartedAt(Instant.now().minusSeconds(1000));
        conversations.saveAndFlush(conversation);
        answer("Recovered");
        chat(owner, active.conversationId(), "Retry after lease expiry", 200);
        assertNull(conversations.findById(active.conversationId()).orElseThrow().getProcessingToken());
    }

    @Test
    void providerFailureIsSanitizedAndReleasesConversation() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        answer("Hello");
        long id = chat(owner, null, "Hi", 200).get("conversationId").asLong();
        OUTPUTS.add(new StubResponse(500, "provider-debug-secret"));
        var failure = chat(owner, id, "Second request", 502);
        assertFalse(failure.toString().contains("provider-debug-secret"));
        assertNull(conversations.findById(id).orElseThrow().getProcessingToken());
        answer("Recovered");
        chat(owner, id, "Try again", 200);
        mvc.perform(get("/agent/conversations/" + id).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void malformedProviderResponsesAndRateLimitsReturnProblemDetail() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        OUTPUTS.add(new StubResponse(200, "{\"choices\":[]}"));
        chat(owner, null, "Hi", 502);
        OUTPUTS.add(new StubResponse(429, "sensitive provider details"));
        chat(owner, null, "Hi again", 503);
        OUTPUTS.add(new StubResponse(200, JSON.writeValueAsString(Map.of("choices", List.of(Map.of(
                "finish_reason", "length", "message", Map.of("role", "assistant", "content", "truncated")))))));
        chat(owner, null, "Another attempt", 502);
    }

    @Test
    void toolRoundsAreBoundedAndFinalRequestDisablesTools() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Order order = order(owner);
        for (int index = 0; index < 3; index++) {
            calls(tool("call-" + index, "get_order", "{\"orderId\":" + order.getId() + "}"));
        }
        chat(owner, null, "Check this order", 502);
        assertEquals(3, INPUTS.size());
        assertFalse(INPUTS.get(2).has("tools"));
        assertEquals("none", INPUTS.get(2).get("tool_choice").asText());
        assertEquals(2, audits.count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void completedWriteAndAuditSurviveFinalProviderFailure() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Order order = order(owner);
        try {
            calls(tool("call-1", "create_ticket", "{\"orderId\":" + order.getId() + ",\"category\":\"DELIVERY\"}"));
            OUTPUTS.add(new StubResponse(500, "provider-debug-secret"));
            chat(owner, null, "Create a delivery ticket for this order", 502);
            var owned = conversations.findByUser_Id(owner.user().getId(), PageRequest.of(0, 10)).getContent();
            assertEquals(1, owned.size());
            long id = owned.getFirst().getId();
            assertNull(owned.getFirst().getProcessingToken());
            assertEquals(1, messages.findByConversation_Id(id, PageRequest.of(0, 10)).getTotalElements());
            assertEquals(1, tickets.findAll().stream().filter(ticket -> ticket.getOrder().getId().equals(order.getId())).count());
            assertEquals(1, audits.findAll().stream().filter(audit -> audit.getUser().getId().equals(owner.user().getId())
                    && audit.getResultStatus() == ToolResultStatus.SUCCESS).count());
            answer("You can continue this conversation.");
            chat(owner, id, "Continue", 200);
        } finally {
            for (Conversation conversation : conversations.findByUser_Id(owner.user().getId(), PageRequest.of(0, 10))) {
                messages.deleteAll(messages.findByConversation_Id(conversation.getId(), PageRequest.of(0, 10)).getContent());
                conversations.deleteById(conversation.getId());
            }
            audits.deleteAll(audits.findAll().stream().filter(audit -> audit.getUser().getId().equals(owner.user().getId())).toList());
            tickets.deleteAll(tickets.findAll().stream().filter(ticket -> ticket.getOrder().getId().equals(order.getId())).toList());
            orders.deleteById(order.getId());
            users.deleteById(owner.user().getId());
        }
    }

    @Test
    void invalidInputAndUnauthenticatedEndpointsAreRejectedWithoutProviderCall() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        chat(owner, null, " ", 400);
        chat(owner, 0L, "Hi", 400);
        chat(owner, null, "x".repeat(4001), 400);
        for (String path : new String[] { "/agent/conversations?size=101", "/agent/conversations?page=-1",
                "/agent/conversations/0", "/agent/conversations/not-an-id" }) {
            mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/agent/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hi\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/agent/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/agent/conversations/1")).andExpect(status().isUnauthorized());
        assertTrue(INPUTS.isEmpty());
    }

    private JsonNode chat(Account owner, Long conversationId, String message, int status) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        if (conversationId != null) { body.put("conversationId", conversationId); }
        String response = mvc.perform(post("/agent/chat").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith(
                        status == 200 ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private Account account(UserRole role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("agent-test-placeholder");
        user.setRole(role);
        users.saveAndFlush(user);
        return new Account(user, sessions.createSession(user).accessToken());
    }

    private Order order(Account account) {
        Order order = new Order();
        order.setCustomer(account.user());
        order.setStatus(OrderStatus.PENDING);
        return orders.saveAndFlush(order);
    }

    private static void answer(String content) {
        OUTPUTS.add(new StubResponse(200, JSON.writeValueAsString(Map.of("choices", List.of(
                Map.of("finish_reason", "stop", "message", Map.of("role", "assistant", "content", content)))))));
    }

    @SafeVarargs
    private static void calls(Map<String, Object>... tools) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", null);
        message.put("tool_calls", List.of(tools));
        OUTPUTS.add(new StubResponse(200, JSON.writeValueAsString(Map.of("choices", List.of(
                Map.of("finish_reason", "tool_calls", "message", message))))));
    }

    private static Map<String, Object> tool(String id, String name, String arguments) {
        return Map.of("id", id, "type", "function", "function", Map.of("name", name, "arguments", arguments));
    }

    private static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                try {
                    INPUTS.add(JSON.readTree(exchange.getRequestBody()));
                    AUTH_HEADERS.add(exchange.getRequestHeaders().getFirst("Authorization"));
                    StubResponse response = OUTPUTS.poll();
                    if (response == null) { response = new StubResponse(500, "No scripted response"); }
                    byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(response.status(), bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            });
            server.start();
            return server;
        } catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private String bearer(Account account) { return "Bearer " + account.token(); }
    private record Account(User user, String token) {
    }
    private record StubResponse(int status, String body) {
    }
}
