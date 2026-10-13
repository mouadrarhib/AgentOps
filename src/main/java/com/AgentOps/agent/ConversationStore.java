package com.AgentOps.agent;

import com.AgentOps.dto.request.AgentChatRequest;
import com.AgentOps.dto.response.*;
import com.AgentOps.entities.Conversation;
import com.AgentOps.entities.Message;
import com.AgentOps.entities.enums.MessageRole;
import com.AgentOps.exceptions.ConflictException;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.helpers.UserHelper;
import com.AgentOps.repositories.ConversationRepository;
import com.AgentOps.repositories.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationStore {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserHelper users;
    private final DeepSeekProperties properties;

    public ConversationStore(ConversationRepository conversations, MessageRepository messages,
                             UserHelper users, DeepSeekProperties properties) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public Turn begin(Long userId, AgentChatRequest request) {
        Conversation conversation;
        if (request.conversationId() == null) {
            conversation = new Conversation();
            conversation.setUser(users.getUserById(userId));
        } else {
            conversation = ownedForUpdate(request.conversationId(), userId);
            if (conversation.getProcessingToken() != null && conversation.getProcessingStartedAt() != null
                    && conversation.getProcessingStartedAt().plusSeconds(properties.turnLeaseSeconds()).isAfter(Instant.now())) {
                throw new ConflictException("A reply is already being generated for this conversation.");
            }
        }
        String token = UUID.randomUUID().toString();
        conversation.setProcessingToken(token);
        conversation.setProcessingStartedAt(Instant.now());
        conversations.saveAndFlush(conversation);
        save(conversation, MessageRole.USER, request.message());
        List<Message> recent = new ArrayList<>(messages.findByConversation_Id(conversation.getId(),
                PageRequest.of(0, properties.contextMessages(), Sort.by(Sort.Direction.DESC, "id"))).getContent());
        List<Map<String, Object>> history = new ArrayList<>();
        int characters = 0;
        for (Message message : recent) {
            if (message.getRole() != MessageRole.USER && message.getRole() != MessageRole.ASSISTANT) { continue; }
            if (characters + message.getContent().length() > 64000) { break; }
            characters += message.getContent().length();
            history.add(Map.of("role", message.getRole() == MessageRole.USER ? "user" : "assistant", "content", message.getContent()));
        }
        Collections.reverse(history);
        return new Turn(conversation.getId(), userId, token, history);
    }

    @Transactional(readOnly = true)
    public void requireActive(Turn turn) {
        Conversation conversation = conversations.findByIdAndUser_Id(turn.conversationId(), turn.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", turn.conversationId()));
        if (!turn.token().equals(conversation.getProcessingToken())) {
            throw new ConflictException("This conversation turn is no longer active.");
        }
    }

    @Transactional
    public MessageResponse finish(Turn turn, String content) {
        Conversation conversation = ownedForUpdate(turn.conversationId(), turn.userId());
        if (!turn.token().equals(conversation.getProcessingToken())) {
            throw new ConflictException("This conversation turn is no longer active.");
        }
        Message message = save(conversation, MessageRole.ASSISTANT, content);
        clearTurn(conversation);
        return messageResponse(message);
    }

    @Transactional
    public void release(Turn turn) {
        conversations.findForUpdate(turn.conversationId(), turn.userId()).ifPresent(conversation -> {
            if (turn.token().equals(conversation.getProcessingToken())) { clearTurn(conversation); }
        });
    }

    @Transactional(readOnly = true)
    public ConversationPageResponse list(Long userId, int page, int size) {
        var result = conversations.findByUser_Id(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new ConversationPageResponse(result.getContent().stream().map(this::conversationResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse detail(Long userId, Long id, int page, int size) {
        Conversation conversation = conversations.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
        var result = messages.findByConversation_Id(id, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
        return new ConversationDetailResponse(conversationResponse(conversation),
                result.getContent().stream().map(this::messageResponse).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private Message save(Conversation conversation, MessageRole role, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        return messages.saveAndFlush(message);
    }

    private Conversation ownedForUpdate(Long id, Long userId) {
        return conversations.findForUpdate(id, userId).orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
    }

    private void clearTurn(Conversation conversation) {
        conversation.setProcessingToken(null);
        conversation.setProcessingStartedAt(null);
        conversations.saveAndFlush(conversation);
    }

    private ConversationResponse conversationResponse(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getUser().getId(), conversation.getCreatedAt());
    }

    private MessageResponse messageResponse(Message message) {
        return new MessageResponse(message.getId(), message.getConversation().getId(), message.getRole(),
                message.getContent(), message.getCreatedAt());
    }

    public record Turn(Long conversationId, Long userId, String token, List<Map<String, Object>> history) {
    }
}
