package com.AgentOps.services;

import com.AgentOps.dto.request.*;
import com.AgentOps.dto.response.*;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserResponse currentUser(String subject);
    AuthResponse refresh(RefreshTokenRequest request);
    void logout(String subject, String sessionId);
    UserResponse updateProfile(String subject, UpdateProfileRequest request);
}
