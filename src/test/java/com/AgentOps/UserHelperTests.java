package com.AgentOps;

import com.AgentOps.entities.User;
import com.AgentOps.exceptions.BadRequestException;
import com.AgentOps.exceptions.ConflictException;
import com.AgentOps.exceptions.ResourceNotFoundException;
import com.AgentOps.helpers.UserHelper;
import com.AgentOps.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserHelperTests {
    @Mock UserRepository users;
    @InjectMocks UserHelper helper;

    @Test
    void returnsUserByIdOrNotFound() {
        User user = new User();
        when(users.findById(42L)).thenReturn(Optional.of(user));
        assertSame(user, helper.getUserById(42L));
        assertThrows(ResourceNotFoundException.class, () -> helper.getUserById(43L));
    }

    @Test
    void rejectsInvalidIdWithoutQueryingDatabase() {
        for (Long id : new Long[] { null, 0L, -1L }) {
            assertThrows(BadRequestException.class, () -> helper.getUserById(id));
        }
        verifyNoInteractions(users);
    }

    @Test
    void emailLookupNormalizesInputAndUsesSafeNotFoundMessage() {
        User user = new User();
        when(users.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(user));
        assertSame(user, helper.getUserByEmail("  PERSON@Example.com  "));
        var error = assertThrows(ResourceNotFoundException.class,
                () -> helper.getUserByEmail("missing@example.com"));
        assertEquals("User was not found.", error.getMessage());
    }

    @Test
    void normalizesEmailIndependentlyOfSystemLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("info@example.com", helper.normalizeEmail("  INFO@EXAMPLE.COM  "));
        } finally {
            Locale.setDefault(original);
        }
        verifyNoInteractions(users);
    }

    @Test
    void rejectsMissingEmailWithoutQueryingDatabase() {
        for (String email : new String[] { null, "", "  " }) {
            assertThrows(BadRequestException.class, () -> helper.getUserByEmail(email));
            assertThrows(BadRequestException.class, () -> helper.ensureEmailAvailable(email));
        }
        verifyNoInteractions(users);
    }

    @Test
    void availabilityCheckRejectsDuplicateAndAllowsUnusedEmail() {
        when(users.existsByEmailIgnoreCase("used@example.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> helper.ensureEmailAvailable(" USED@example.com "));
        assertDoesNotThrow(() -> helper.ensureEmailAvailable("new@example.com"));
    }
}
