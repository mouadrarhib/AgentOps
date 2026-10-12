package com.AgentOps.services.servicesImp;

import com.AgentOps.dto.request.*;
import com.AgentOps.dto.response.*;
import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.exceptions.*;
import com.AgentOps.repositories.UserRepository;
import com.AgentOps.security.JwtService;
import com.AgentOps.services.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository users, PasswordEncoder passwords, JwtService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyPasswordHash = passwords.encode("unused-account-timing-comparison");
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(UserRole.CUSTOMER);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("An account with this email already exists.");
        }
        return authenticated(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalize(request.email())).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return authenticated(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(String subject) {
        long id;
        try { id = Long.parseLong(subject); }
        catch (NumberFormatException exception) { throw new UnauthorizedException(); }
        return response(users.findById(id).orElseThrow(UnauthorizedException::new));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse authenticated(User user) {
        return new AuthResponse(tokens.generateToken(user), "Bearer", tokens.expirationSeconds(), response(user));
    }

    private UserResponse response(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
