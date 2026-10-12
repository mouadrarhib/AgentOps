package com.AgentOps;

import com.AgentOps.dto.request.*;
import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.exceptions.*;
import com.AgentOps.helpers.UserHelper;
import com.AgentOps.repositories.UserRepository;
import com.AgentOps.security.JwtService;
import com.AgentOps.services.servicesImp.AuthServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTests {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock JwtService tokens;
    AuthServiceImpl service;

    @BeforeEach
    void setup() {
        when(passwords.encode("unused-account-timing-comparison")).thenReturn("dummy-hash");
        service = new AuthServiceImpl(users, new UserHelper(users), passwords, tokens);
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndUsesCustomerRole() {
        when(passwords.encode("password123")).thenReturn("stored-hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 42L);
            return user;
        });
        when(tokens.generateToken(any())).thenReturn("signed-token");
        when(tokens.expirationSeconds()).thenReturn(3600L);
        var result = service.register(new RegisterRequest("Person@Example.com", "password123"));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        assertEquals("person@example.com", captor.getValue().getEmail());
        assertEquals("stored-hash", captor.getValue().getPasswordHash());
        assertEquals(UserRole.CUSTOMER, result.user().getRole());
        assertEquals("signed-token", result.accessToken());
        assertEquals(3600L, result.expiresIn());
    }

    @Test
    void duplicateEmailIsRejectedBeforeSaving() {
        when(users.existsByEmailIgnoreCase("person@example.com")).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.register(new RegisterRequest("PERSON@example.com", "password123")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicateEmailRaceReturnsConflict() {
        when(passwords.encode("password123")).thenReturn("hash");
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));
        assertThrows(ConflictException.class,
                () -> service.register(new RegisterRequest("person@example.com", "password123")));
    }

    @Test
    void validLoginIssuesToken() {
        User user = user();
        when(users.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("password123", "stored-hash")).thenReturn(true);
        when(tokens.generateToken(user)).thenReturn("signed-token");
        assertEquals("signed-token", service.login(new LoginRequest("PERSON@example.com", "password123")).accessToken());
    }

    @Test
    void unknownAccountStillPerformsPasswordComparison() {
        when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("unknown@example.com", "password123")));
        verify(passwords).matches("password123", "dummy-hash");
        verifyNoInteractions(tokens);
    }

    @Test
    void wrongPasswordDoesNotIssueToken() {
        when(users.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(user()));
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("person@example.com", "wrong-password")));
        verifyNoInteractions(tokens);
    }

    @Test
    void currentUserReturnsPublicFieldsAndRejectsDeletedOrInvalidAccounts() {
        when(users.findById(42L)).thenReturn(Optional.of(user()));
        assertEquals("person@example.com", service.currentUser("42").getEmail());
        assertThrows(UnauthorizedException.class, () -> service.currentUser("unknown"));
        assertThrows(UnauthorizedException.class, () -> service.currentUser("43"));
    }

    private User user() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setEmail("person@example.com");
        user.setPasswordHash("stored-hash");
        user.setRole(UserRole.CUSTOMER);
        return user;
    }
}
