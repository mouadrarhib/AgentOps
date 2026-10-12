package com.AgentOps.services.servicesImp;

import com.AgentOps.dto.request.*;
import com.AgentOps.dto.response.*;
import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.exceptions.*;
import com.AgentOps.helpers.UserHelper;
import com.AgentOps.repositories.UserRepository;
import com.AgentOps.services.AuthSessionService;
import com.AgentOps.services.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository users;
    private final UserHelper userHelper;
    private final PasswordEncoder passwords;
    private final AuthSessionService sessions;
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository users, UserHelper userHelper, PasswordEncoder passwords, AuthSessionService sessions) {
        this.users = users;
        this.userHelper = userHelper;
        this.passwords = passwords;
        this.sessions = sessions;
        this.dummyPasswordHash = passwords.encode("unused-account-timing-comparison");
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = userHelper.normalizeEmail(request.email());
        userHelper.ensureEmailAvailable(email);
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(UserRole.CUSTOMER);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("An account with this email already exists.");
        }
        return sessions.createSession(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findForUpdateByEmail(userHelper.normalizeEmail(request.email())).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return sessions.createSession(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(String subject) {
        return response(requireUser(subject));
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        return sessions.refresh(request.refreshToken());
    }

    @Override
    public void logout(String subject, String sessionId) {
        sessions.logout(subject, sessionId);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String subject, UpdateProfileRequest request) {
        User user = users.findForUpdateById(userId(subject)).orElseThrow(UnauthorizedException::new);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (request.email() != null) {
            String email = userHelper.normalizeEmail(request.email());
            if (!email.equalsIgnoreCase(user.getEmail())) {
                userHelper.ensureEmailAvailable(email);
            }
            user.setEmail(email);
        }
        if (request.password() != null) {
            user.setPasswordHash(passwords.encode(request.password()));
        }
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("An account with this email already exists.");
        }
        UserResponse result = response(user);
        if (request.password() != null) {
            sessions.revokeUserSessions(user.getId());
        }
        return result;
    }

    private User requireUser(String subject) {
        try {
            return userHelper.getUserById(userId(subject));
        } catch (ResourceNotFoundException exception) {
            throw new UnauthorizedException();
        }
    }

    private long userId(String subject) {
        long id;
        try { id = Long.parseLong(subject); }
        catch (NumberFormatException exception) { throw new UnauthorizedException(); }
        if (id <= 0) {
            throw new UnauthorizedException();
        }
        return id;
    }

    private UserResponse response(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
