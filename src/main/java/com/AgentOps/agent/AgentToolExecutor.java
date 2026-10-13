package com.AgentOps.agent;

import com.AgentOps.dto.request.*;
import com.AgentOps.entities.enums.*;
import com.AgentOps.exceptions.ApiException;
import com.AgentOps.exceptions.BadRequestException;
import com.AgentOps.services.OrderService;
import com.AgentOps.services.TicketService;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Component
public class AgentToolExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolExecutor.class);
    private final OrderService orders;
    private final TicketService tickets;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final ToolAuditService audits;
    private final Map<String, Tool> registry;

    public AgentToolExecutor(OrderService orders, TicketService tickets, Validator validator,
                             ObjectMapper mapper, ToolAuditService audits) {
        this.orders = orders;
        this.tickets = tickets;
        this.validator = validator;
        this.mapper = mapper;
        this.audits = audits;
        Map<String, Parameter> paging = Map.of("page", integer(0, 10000), "size", integer(1, 20));
        Map<String, Parameter> filters = new LinkedHashMap<>(paging);
        filters.put("status", enumeration(TicketStatus.values()));
        filters.put("priority", enumeration(TicketPriority.values()));
        filters.put("category", enumeration(TicketCategory.values()));
        filters.put("orderId", id());
        filters.put("customerId", id());
        filters.put("assignedToId", id());
        filters.put("unassigned", new Parameter("boolean", 0, 0, List.of()));
        registry = new LinkedHashMap<>();
        add("get_order", "Get one order visible to the authenticated user.", Map.of("orderId", id()), List.of("orderId"));
        add("list_orders", "List visible orders; page defaults to 0 and size to 20.", paging, List.of());
        add("get_ticket", "Get one ticket visible to the authenticated user.", Map.of("ticketId", id()), List.of("ticketId"));
        add("list_tickets", "List visible tickets with optional filters and pagination.", filters, List.of());
        add("create_ticket", "Create a support ticket for an authorized order, only when the user requests it.",
                Map.of("orderId", id(), "category", enumeration(TicketCategory.values())), List.of("orderId", "category"));
        add("update_ticket", "Update a ticket only when explicitly requested. SUPPORT/ADMIN only. Use unassign=true to clear assignment.",
                Map.of("ticketId", id(), "status", enumeration(TicketStatus.values()), "priority", enumeration(TicketPriority.values()),
                        "assignedToId", id(), "unassign", new Parameter("boolean", 0, 0, List.of())), List.of("ticketId"));
    }

    public List<Map<String, Object>> definitions(boolean staff) {
        return registry.values().stream().filter(tool -> staff || !tool.name().equals("update_ticket"))
                .map(Tool::definition).toList();
    }

    public ToolResult execute(Long userId, DeepSeekClient.ToolCall call) {
        Tool tool = registry.get(call.name());
        if (tool == null) {
            return record(userId, call.name(), "Unknown tool rejected.", ToolResultStatus.DENIED, 403, "This tool is not allowed.");
        }
        String summary = "Arguments rejected.";
        try {
            JsonNode arguments;
            try { arguments = mapper.readTree(call.arguments()); }
            catch (RuntimeException exception) { throw new BadRequestException("Tool arguments must be a JSON object."); }
            validateArguments(tool, arguments);
            summary = mapper.writeValueAsString(arguments);
            Object result = switch (call.name()) {
                case "get_order" -> orders.getOrder(number(arguments, "orderId"));
                case "list_orders" -> orders.listOrders(page(arguments), size(arguments));
                case "get_ticket" -> tickets.getTicket(number(arguments, "ticketId"));
                case "list_tickets" -> tickets.listTickets(validated(new TicketFilterRequest(
                        enumeration(arguments, "status", TicketStatus.class), enumeration(arguments, "priority", TicketPriority.class),
                        enumeration(arguments, "category", TicketCategory.class), number(arguments, "orderId"),
                        number(arguments, "customerId"), number(arguments, "assignedToId"), bool(arguments, "unassigned"))),
                        page(arguments), size(arguments));
                case "create_ticket" -> tickets.createTicket(validated(new CreateTicketRequest(number(arguments, "orderId"),
                        enumeration(arguments, "category", TicketCategory.class))));
                case "update_ticket" -> tickets.updateTicket(number(arguments, "ticketId"), validated(new UpdateTicketRequest(
                        enumeration(arguments, "status", TicketStatus.class), enumeration(arguments, "priority", TicketPriority.class),
                        number(arguments, "assignedToId"), bool(arguments, "unassign"))));
                default -> throw new BadRequestException("Unknown tool.");
            };
            String content = mapper.writeValueAsString(Map.of("data", result));
            if (content.length() > 20000) { throw new BadRequestException("Tool result is too large. Request a smaller page."); }
            audits.record(userId, call.name(), summary, ToolResultStatus.SUCCESS);
            return new ToolResult(content, ToolResultStatus.SUCCESS);
        } catch (ApiException exception) {
            ToolResultStatus status = exception.getStatus().value() == 403 || exception.getStatus().value() == 404
                    || exception.getStatus().value() == 401 ? ToolResultStatus.DENIED : ToolResultStatus.FAILURE;
            return record(userId, call.name(), summary, status, exception.getStatus().value(), exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Agent tool {} failed", call.name(), exception);
            return record(userId, call.name(), summary, ToolResultStatus.FAILURE, 500, "The backend tool failed.");
        }
    }

    private ToolResult record(Long userId, String name, String summary, ToolResultStatus status, int code, String message) {
        audits.record(userId, name, summary, status);
        return new ToolResult(mapper.writeValueAsString(Map.of("error", Map.of("status", code, "message", message))), status);
    }

    private void validateArguments(Tool tool, JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) { throw new BadRequestException("Tool arguments must be a JSON object."); }
        for (String required : tool.required()) {
            if (!arguments.has(required)) { throw new BadRequestException("Missing required tool argument: " + required); }
        }
        for (var field : arguments.properties()) {
            Parameter parameter = tool.parameters().get(field.getKey());
            if (parameter == null) { throw new BadRequestException("Unknown tool argument."); }
            JsonNode value = field.getValue();
            boolean valid = switch (parameter.type()) {
                case "integer" -> value.isIntegralNumber() && value.canConvertToLong()
                        && value.asLong() >= parameter.min() && value.asLong() <= parameter.max();
                case "boolean" -> value.isBoolean();
                case "string" -> value.isString() && parameter.values().contains(value.asText());
                default -> false;
            };
            if (!valid) { throw new BadRequestException("Invalid tool argument: " + field.getKey()); }
        }
    }

    private <T> T validated(T request) {
        if (!validator.validate(request).isEmpty()) { throw new BadRequestException("Tool arguments failed request validation."); }
        return request;
    }

    private Long number(JsonNode arguments, String name) { return arguments.has(name) ? arguments.get(name).asLong() : null; }
    private Boolean bool(JsonNode arguments, String name) { return arguments.has(name) ? arguments.get(name).asBoolean() : null; }
    private int page(JsonNode arguments) { return arguments.has("page") ? arguments.get("page").asInt() : 0; }
    private int size(JsonNode arguments) { return arguments.has("size") ? arguments.get("size").asInt() : 20; }
    private <T extends Enum<T>> T enumeration(JsonNode arguments, String name, Class<T> type) {
        return arguments.has(name) ? Enum.valueOf(type, arguments.get(name).asText()) : null;
    }
    private Parameter id() { return integer(1, Long.MAX_VALUE); }
    private Parameter integer(long min, long max) { return new Parameter("integer", min, max, List.of()); }
    private Parameter enumeration(Enum<?>[] values) {
        return new Parameter("string", 0, 0, Arrays.stream(values).map(Enum::name).toList());
    }
    private void add(String name, String description, Map<String, Parameter> parameters, List<String> required) {
        registry.put(name, new Tool(name, description, parameters, required));
    }

    public record ToolResult(String content, ToolResultStatus status) {
    }
    private record Parameter(String type, long min, long max, List<String> values) {
        Map<String, Object> schema() {
            if (type.equals("integer")) { return Map.of("type", type, "minimum", min, "maximum", max); }
            return values.isEmpty() ? Map.of("type", type) : Map.of("type", type, "enum", values);
        }
    }
    private record Tool(String name, String description, Map<String, Parameter> parameters, List<String> required) {
        Map<String, Object> definition() {
            Map<String, Object> properties = new LinkedHashMap<>();
            parameters.forEach((name, parameter) -> properties.put(name, parameter.schema()));
            return Map.of("type", "function", "function", Map.of("name", name, "description", description,
                    "parameters", Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false)));
        }
    }
}
