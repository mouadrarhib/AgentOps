package com.AgentOps.repositories;

import com.AgentOps.entities.AuthSession;
import com.AgentOps.entities.enums.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :id")
    Optional<AuthSession> findForUpdateById(@Param("id") Long id);

    boolean existsByIdAndUser_IdAndUser_RoleAndRevokedFalseAndExpiresAtAfter(
            Long id, Long userId, UserRole role, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthSession session set session.revoked = true where session.user.id = :userId")
    void revokeByUserId(@Param("userId") Long userId);
}
