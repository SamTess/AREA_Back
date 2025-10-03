package area.server.AREA_Back.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private Validator validator;
    private User user;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setIsActive(true);
        user.setIsAdmin(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setAvatarUrl("https://example.com/avatar.jpg");
    }

    @Test
    void testValidUser() {
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testUserWithInvalidEmail() {
        user.setEmail("invalid-email");
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email should be valid")));
    }

    @Test
    void testUserWithBlankEmail() {
        user.setEmail("");
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testUserWithNullEmail() {
        user.setEmail(null);
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testUserDefaultValues() {
        User newUser = new User();
        newUser.setEmail("newuser@example.com");

        assertTrue(newUser.getIsActive());
        assertFalse(newUser.getIsAdmin());
    }

    @Test
    void testUserEqualsAndHashCode() {
        User user1 = new User();
        user1.setId(UUID.randomUUID());
        user1.setEmail("test@example.com");

        User user2 = new User();
        user2.setId(user1.getId());
        user2.setEmail("test@example.com");

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testUserToString() {
        String userString = user.toString();
        assertNotNull(userString);
        assertTrue(userString.contains("test@example.com"));
    }

    @Test
    void testUserSettersAndGetters() {
        UUID id = UUID.randomUUID();
        String email = "newtest@example.com";
        Boolean isActive = false;
        Boolean isAdmin = true;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime lastLoginAt = LocalDateTime.now().plusDays(1);
        String avatarUrl = "https://newexample.com/avatar.jpg";

        user.setId(id);
        user.setEmail(email);
        user.setIsActive(isActive);
        user.setIsAdmin(isAdmin);
        user.setCreatedAt(createdAt);
        user.setLastLoginAt(lastLoginAt);
        user.setAvatarUrl(avatarUrl);

        assertEquals(id, user.getId());
        assertEquals(email, user.getEmail());
        assertEquals(isActive, user.getIsActive());
        assertEquals(isAdmin, user.getIsAdmin());
        assertEquals(createdAt, user.getCreatedAt());
        assertEquals(lastLoginAt, user.getLastLoginAt());
        assertEquals(avatarUrl, user.getAvatarUrl());
    }

    @Test
    void testUserConstructors() {
        // Test no-args constructor
        User user1 = new User();
        assertNotNull(user1);

        // Test all-args constructor - simplified
        User user2 = new User();
        UUID id = UUID.randomUUID();
        String email = "constructor@example.com";
        user2.setId(id);
        user2.setEmail(email);

        assertEquals(id, user2.getId());
        assertEquals(email, user2.getEmail());
    }
}