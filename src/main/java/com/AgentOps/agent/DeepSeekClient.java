package com.AgentOps.agent;

import com.AgentOps.exceptions.AiProviderException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekClient {
    private final DeepSeekProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI endpoint;

    public DeepSeekClient(DeepSeekProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        URI base = properties.baseUrl();
        if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalStateException("DEEPSEEK_BASE_URL must be an HTTP(S) API base URL.");
        }
        endpoint = URI.create(base.toString().replaceAll("/+$", "") + "/chat/completions");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public void requireConfigured() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiProviderException(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is not configured.");
        }
    }

    public AssistantTurn complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>(Map.of("model", properties.model(), "messages", messages,
                "stream", false, "thinking", Map.of("type", "disabled"), "max_tokens", properties.maxOutputTokens()));
        if (!tools.isEmpty()) { body.put("tools", tools); }
        else { body.put("tool_choice", "none"); }
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Authorization", "Bearer " + properties.apiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new AiProviderException(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is busy. Try again later.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length() > 1_000_000) {
                throw invalidResponse();
            }
            return parse(response.body());
        } catch (HttpTimeoutException exception) {
            throw new AiProviderException(HttpStatus.GATEWAY_TIMEOUT, "The AI provider timed out.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(HttpStatus.SERVICE_UNAVAILABLE, "The AI request was interrupted.");
        } catch (IOException exception) {
            throw new AiProviderException(HttpStatus.BAD_GATEWAY, "The AI provider could not be reached.");
        }
    }

    private AssistantTurn parse(String body) {
        try {
            JsonNode choices = mapper.readTree(body).path("choices");
            if (!choices.isArray() || choices.isEmpty()) { throw invalidResponse(); }
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            String finish = choice.path("finish_reason").asText();
            if (!"assistant".equals(message.path("role").asText())) { throw invalidResponse(); }
            String content = message.path("content").isString() ? message.get("content").asText() : null;
            if (content != null && content.length() > 20000) { throw invalidResponse(); }
            List<ToolCall> calls = new ArrayList<>();
            JsonNode rawCalls = message.path("tool_calls");
            if (!rawCalls.isMissingNode() && !rawCalls.isNull() && !rawCalls.isArray()) { throw invalidResponse(); }
            if (rawCalls.isArray()) {
                if (rawCalls.size() > properties.maxToolCalls()) { throw invalidResponse(); }
                for (JsonNode call : rawCalls) {
                    JsonNode function = call.path("function");
                    if (!"function".equals(call.path("type").asText()) || !call.path("id").isString()
                            || call.get("id").asText().isBlank() || call.get("id").asText().length() > 128
                            || !function.path("name").isString() || !function.get("name").asText().matches("[A-Za-z0-9_-]{1,128}")
                            || !function.path("arguments").isString()
                            || function.get("arguments").asText().length() > 4000) { throw invalidResponse(); }
                    calls.add(new ToolCall(call.get("id").asText(), function.get("name").asText(), function.get("arguments").asText()));
                }
            }
            if (!calls.isEmpty()) {
                if (!"tool_calls".equals(finish) || calls.stream().map(ToolCall::id).distinct().count() != calls.size()) {
                    throw invalidResponse();
                }
            } else if (!"stop".equals(finish) || content == null || content.isBlank() || content.length() > 20000) {
                throw invalidResponse();
            }
            return new AssistantTurn(content, calls);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private AiProviderException invalidResponse() {
        return new AiProviderException(HttpStatus.BAD_GATEWAY, "The AI provider returned an invalid response.");
    }

    public record ToolCall(String id, String name, String arguments) {
    }

    public record AssistantTurn(String content, List<ToolCall> toolCalls) {
        public Map<String, Object> toMessage() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("role", "assistant");
            result.put("content", content);
            if (!toolCalls.isEmpty()) {
                result.put("tool_calls", toolCalls.stream().map(call -> Map.of("id", call.id(), "type", "function",
                        "function", Map.of("name", call.name(), "arguments", call.arguments()))).toList());
            }
            return result;
        }
    }
}
