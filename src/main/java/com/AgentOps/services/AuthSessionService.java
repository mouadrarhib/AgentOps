package com.AgentOps.services;

import com.AgentOps.dto.response.AuthResponse;
import com.AgentOps.entities.User;

public interface AuthSessionService {
    AuthResponse createSession(User user);
    AuthResponse refresh(String refreshToken);
    void logout(String subject, String sessionId);
    void revokeUserSessions(Long userId);
}
