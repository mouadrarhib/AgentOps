package com.AgentOps.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(@Positive Long conversationId,
                                @NotBlank @Size(max = 4000) String message) {
    @Override
    public String toString() { return "AgentChatRequest[conversationId=" + conversationId + "]"; }
}
