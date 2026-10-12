package com.AgentOps.services.servicesImp;

import com.AgentOps.dto.response.AuthResponse;
import com.AgentOps.dto.response.UserResponse;
import com.AgentOps.entities.AuthSession;
import com.AgentOps.entities.User;
import com.AgentOps.exceptions.InvalidRefreshTokenException;
import com.AgentOps.exceptions.UnauthorizedException;
import com.AgentOps.repositories.AuthSessionRepository;
import com.AgentOps.security.JwtService;
import com.AgentOps.security.RefreshTokenProperties;
import com.AgentOps.services.AuthSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Transactional
public class AuthSessionServiceImpl implements AuthSessionService {
    private final AuthSessionRepository sessions;
    private final JwtService tokens;
    private final RefreshTokenProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionServiceImpl(AuthSessionRepository sessions, JwtService tokens, RefreshTokenProperties properties) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Override
    public AuthResponse createSession(User user) {
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(session.getCreatedAt().plusSeconds(properties.expirationSeconds()));
        String secret = newSecret();
        session.setRefreshTokenHash(hash(secret));
        sessions.saveAndFlush(session);
        return response(session, secret);
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || !refreshToken.matches("[1-9][0-9]{0,18}\\.[A-Za-z0-9_-]{43}")) {
            throw new InvalidRefreshTokenException();
        }
        int separator = refreshToken.indexOf('.');
        long id;
        try {
            id = Long.parseLong(refreshToken.substring(0, separator));
        } catch (NumberFormatException exception) {
            throw new InvalidRefreshTokenException();
        }
        AuthSession session = sessions.findForUpdateById(id).orElseThrow(InvalidRefreshTokenException::new);
        String presentedHash = hash(refreshToken.substring(separator + 1));
        if (session.isRevoked() || !session.getExpiresAt().isAfter(Instant.now()) ||
                !MessageDigest.isEqual(presentedHash.getBytes(StandardCharsets.US_ASCII),
                        session.getRefreshTokenHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidRefreshTokenException();
        }
        String secret = newSecret();
        session.setRefreshTokenHash(hash(secret));
        sessions.saveAndFlush(session);
        return response(session, secret);
    }

    @Override
    public void logout(String subject, String sessionId) {
        long id;
        long userId;
        try {
            id = Long.parseLong(sessionId);
            userId = Long.parseLong(subject);
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException();
        }
        AuthSession session = sessions.findForUpdateById(id).orElseThrow(UnauthorizedException::new);
        if (!session.getUser().getId().equals(userId)) {
            throw new UnauthorizedException();
        }
        session.setRevoked(true);
        sessions.saveAndFlush(session);
    }

    @Override
    public void revokeUserSessions(Long userId) {
        sessions.revokeByUserId(userId);
    }

    private AuthResponse response(AuthSession session, String secret) {
        User user = session.getUser();
        long refreshExpiresIn = Math.max(0, Duration.between(Instant.now(), session.getExpiresAt()).toSeconds());
        return new AuthResponse(tokens.generateToken(user, session.getId(), session.getExpiresAt()), "Bearer",
                Math.min(tokens.expirationSeconds(), refreshExpiresIn),
                session.getId() + "." + secret, refreshExpiresIn,
                new UserResponse(user.getId(), user.getEmail(), user.getRole()));
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
