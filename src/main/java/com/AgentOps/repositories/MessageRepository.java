package com.AgentOps.repositories;

import com.AgentOps.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversation_Id(Long conversationId, Pageable pageable);
}
