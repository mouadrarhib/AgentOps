package com.AgentOps.repositories;

import com.AgentOps.entities.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Page<Conversation> findByUser_Id(Long userId, Pageable pageable);
    Optional<Conversation> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from Conversation conversation where conversation.id = :id and conversation.user.id = :userId")
    Optional<Conversation> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
