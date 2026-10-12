package com.AgentOps.helpers;

import com.AgentOps.entities.User;
import com.AgentOps.exceptions.BadRequestException;
import com.AgentOps.exceptions.ConflictException;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.repositories.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class UserHelper {
    private final UserRepository users;

    public UserHelper(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("User ID must be positive.");
        }
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return users.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("User"));
    }

    public String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email must not be blank.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** The database unique constraint must still handle concurrent registrations. */
    @Transactional(readOnly = true)
    public void ensureEmailAvailable(String email) {
        if (users.existsByEmailIgnoreCase(normalizeEmail(email))) {
            throw new ConflictException("An account with this email already exists.");
        }
    }
}
