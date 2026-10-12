package com.AgentOps.dto.response;

import com.AgentOps.entities.enums.MessageRole;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private Long id;

    private Long conversationId;

    private MessageRole role;

    private String content;

    private Instant createdAt;
}
