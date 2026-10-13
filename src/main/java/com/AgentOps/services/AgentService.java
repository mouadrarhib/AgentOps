package com.AgentOps.services;

import com.AgentOps.dto.request.AgentChatRequest;
import com.AgentOps.dto.response.AgentChatResponse;
import com.AgentOps.dto.response.ConversationDetailResponse;
import com.AgentOps.dto.response.ConversationPageResponse;

public interface AgentService {
    AgentChatResponse chat(AgentChatRequest request);
    ConversationPageResponse listConversations(int page, int size);
    ConversationDetailResponse getConversation(Long conversationId, int page, int size);
}
