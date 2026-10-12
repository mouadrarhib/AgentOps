package com.AgentOps.helpers;

import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SecurityHelper {
    private final UserHelper users;

    public SecurityHelper(UserHelper users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        try {
            return users.getUserById(getCurrentUserId());
        } catch (ResourceNotFoundException exception) {
            throw new UnauthorizedException();
        }
    }

    /** Reads the validated JWT subject; does not query the database. */
    public Long getCurrentUserId() {
        JwtAuthenticationToken authentication = currentAuthentication();
        if (authentication == null) {
            throw new UnauthorizedException();
        }
        try {
            long id = Long.parseLong(authentication.getToken().getSubject());
            if (id <= 0) {
                throw new UnauthorizedException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException();
        }
    }

    /** Returns false for anonymous requests or a missing resource owner ID. */
    public boolean isCurrentUser(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return userId.equals(getCurrentUserId());
        } catch (UnauthorizedException exception) {
            return false;
        }
    }

    /** Uses token authorities, matching Spring Security's hasRole checks. */
    public boolean hasRole(UserRole role) {
        JwtAuthenticationToken authentication = currentAuthentication();
        return role != null && authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }

    private JwtAuthenticationToken currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated() ? jwt : null;
    }
}
