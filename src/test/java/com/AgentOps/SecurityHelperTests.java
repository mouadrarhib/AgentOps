package com.AgentOps;

import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.exceptions.UnauthorizedException;
import com.AgentOps.helpers.SecurityHelper;
import com.AgentOps.helpers.UserHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityHelperTests {
    @Mock UserHelper users;
    @InjectMocks SecurityHelper helper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsSubjectAndOwnershipWithoutDatabaseLookup() {
        authenticate("42", "ROLE_CUSTOMER");
        assertEquals(42L, helper.getCurrentUserId());
        assertTrue(helper.isCurrentUser(42L));
        assertFalse(helper.isCurrentUser(43L));
        assertFalse(helper.isCurrentUser(null));
        verifyNoInteractions(users);
    }

    @Test
    void resolvesCurrentUserFromDatabase() {
        authenticate("42", "ROLE_CUSTOMER");
        User user = new User();
        when(users.getUserById(42L)).thenReturn(user);
        assertSame(user, helper.getCurrentUser());
    }

    @Test
    void deletedCurrentUserReturnsUnauthorizedRatherThanNotFound() {
        authenticate("42", "ROLE_CUSTOMER");
        when(users.getUserById(42L)).thenThrow(new ResourceNotFoundException("User", 42L));
        assertThrows(UnauthorizedException.class, helper::getCurrentUser);
    }

    @Test
    void rolesUseGrantedAuthoritiesRatherThanUnconvertedClaims() {
        authenticate("42", "ROLE_SUPPORT");
        assertTrue(helper.hasRole(UserRole.SUPPORT));
        assertFalse(helper.hasRole(UserRole.ADMIN));
        assertFalse(helper.hasRole(null));
        verifyNoInteractions(users);
    }

    @Test
    void missingAuthenticationIsRejectedAndPredicatesReturnFalse() {
        assertUnauthenticated();
    }

    @Test
    void anonymousAuthenticationCannotResolveUserOrRoles() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertUnauthenticated();
    }

    @Test
    void unauthenticatedJwtCannotResolveUserOrRoles() {
        authenticate("42", "ROLE_ADMIN");
        SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);
        assertUnauthenticated();
    }

    @Test
    void invalidSubjectsReturnUnauthorizedWithoutQueryingDatabase() {
        for (String subject : new String[] { "invalid", "0", "-1", "999999999999999999999" }) {
            authenticate(subject, "ROLE_CUSTOMER");
            assertThrows(UnauthorizedException.class, helper::getCurrentUserId);
            assertThrows(UnauthorizedException.class, helper::getCurrentUser);
            assertFalse(helper.isCurrentUser(42L));
        }
        verifyNoInteractions(users);
    }

    private void assertUnauthenticated() {
        assertThrows(UnauthorizedException.class, helper::getCurrentUserId);
        assertThrows(UnauthorizedException.class, helper::getCurrentUser);
        assertFalse(helper.isCurrentUser(42L));
        assertFalse(helper.hasRole(UserRole.ADMIN));
        verifyNoInteractions(users);
    }

    private void authenticate(String subject, String authority) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject(subject).claim("role", "ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority(authority))));
    }
}
