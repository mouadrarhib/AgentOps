package com.AgentOps.dto.response;

import java.util.List;

public record ConversationDetailResponse(ConversationResponse conversation, List<MessageResponse> messages,
                                          int page, int size, long totalElements, int totalPages) {
}
