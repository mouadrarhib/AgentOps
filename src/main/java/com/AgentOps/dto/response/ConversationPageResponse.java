package com.AgentOps.dto.response;

import java.util.List;

public record ConversationPageResponse(List<ConversationResponse> content, int page, int size,
                                        long totalElements, int totalPages) {
}
