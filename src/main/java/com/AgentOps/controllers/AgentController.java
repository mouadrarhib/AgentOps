package com.AgentOps.controllers;

import com.AgentOps.dto.request.AgentChatRequest;
import com.AgentOps.dto.response.AgentChatResponse;
import com.AgentOps.dto.response.ConversationDetailResponse;
import com.AgentOps.dto.response.ConversationPageResponse;
import com.AgentOps.services.AgentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent")
@SecurityRequirement(name = "bearerAuth")
public class AgentController {
    private final AgentService agent;

    public AgentController(AgentService agent) { this.agent = agent; }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) { return agent.chat(request); }

    @GetMapping("/conversations")
    public ConversationPageResponse list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return agent.listConversations(page, size);
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationDetailResponse detail(@PathVariable @Positive Long conversationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return agent.getConversation(conversationId, page, size);
    }
}
