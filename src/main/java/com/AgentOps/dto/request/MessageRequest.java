package com.AgentOps.dto.request;

import com.AgentOps.entities.enums.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {
    @NotNull(message = "conversationId is required")
    @Positive(message = "conversationId must be positive")
    private Long conversationId;

    @NotNull(message = "role is required")
    private MessageRole role;

    @NotBlank(message = "Content is required")
    private String content;
}
