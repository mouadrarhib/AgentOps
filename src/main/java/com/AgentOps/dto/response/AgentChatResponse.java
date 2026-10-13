package com.AgentOps.dto.response;

public record AgentChatResponse(Long conversationId, MessageResponse reply, int toolCalls) {
}
