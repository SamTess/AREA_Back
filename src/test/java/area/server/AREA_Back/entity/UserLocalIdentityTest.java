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

class UserLocalIdentityTest {

    private Validator validator;
    private UserLocalIdentity userLocalIdentity;
    private User user;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setIsActive(true);

        userLocalIdentity = new UserLocalIdentity();
        userLocalIdentity.setId(UUID.randomUUID());
        userLocalIdentity.setUser(user);
        userLocalIdentity.setEmail("test@example.com");
        userLocalIdentity.setPasswordHash("$2a$10$hashedPassword");
        userLocalIdentity.setIsEmailVerified(false);
        userLocalIdentity.setFailedLoginAttempts(0);
        userLocalIdentity.setCreatedAt(LocalDateTime.now());
        userLocalIdentity.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testValidUserLocalIdentity() {
        Set<ConstraintViolation<UserLocalIdentity>> violations = validator.validate(userLocalIdentity);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testUserLocalIdentityWithInvalidEmail() {
        userLocalIdentity.setEmail("invalid-email");
        Set<ConstraintViolation<UserLocalIdentity>> violations = validator.validate(userLocalIdentity);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email should be valid")));
    }

    @Test
    void testUserLocalIdentityWithNullEmail() {
        userLocalIdentity.setEmail(null);
        Set<ConstraintViolation<UserLocalIdentity>> violations = validator.validate(userLocalIdentity);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testUserLocalIdentityWithBlankEmail() {
        userLocalIdentity.setEmail("   ");
        Set<ConstraintViolation<UserLocalIdentity>> violations = validator.validate(userLocalIdentity);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Email is required")));
    }

    @Test
    void testUserLocalIdentityWithNullPasswordHash() {
        userLocalIdentity.setPasswordHash(null);
        Set<ConstraintViolation<UserLocalIdentity>> violations = validator.validate(userLocalIdentity);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Password hash is required")));
    }

    @Test
    void testUserLocalIdentityWithBlankPasswordHash() {
        userLocalIdentity.setPasswordHash("   ");
        Set<ConstraintViolation<UserLocalIdentity>> violations = validator.validate(userLocalIdentity);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Password hash is required")));
    }

    @Test
    void testIsAccountLockedWhenNotLocked() {
        userLocalIdentity.setLockedUntil(null);
        assertFalse(userLocalIdentity.isAccountLocked());
    }

    @Test
    void testIsAccountLockedWhenLockExpired() {
        userLocalIdentity.setLockedUntil(LocalDateTime.now().minusMinutes(5));
        assertFalse(userLocalIdentity.isAccountLocked());
    }

    @Test
    void testIsAccountLockedWhenCurrentlyLocked() {
        userLocalIdentity.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        assertTrue(userLocalIdentity.isAccountLocked());
    }

    @Test
    void testIsEmailVerificationTokenValidWhenTokenIsNull() {
        userLocalIdentity.setEmailVerificationToken(null);
        userLocalIdentity.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(1));
        assertFalse(userLocalIdentity.isEmailVerificationTokenValid());
    }

    @Test
    void testIsEmailVerificationTokenValidWhenExpirationIsNull() {
        userLocalIdentity.setEmailVerificationToken("token123");
        userLocalIdentity.setEmailVerificationExpiresAt(null);
        assertFalse(userLocalIdentity.isEmailVerificationTokenValid());
    }

    @Test
    void testIsEmailVerificationTokenValidWhenTokenExpired() {
        userLocalIdentity.setEmailVerificationToken("token123");
        userLocalIdentity.setEmailVerificationExpiresAt(LocalDateTime.now().minusHours(1));
        assertFalse(userLocalIdentity.isEmailVerificationTokenValid());
    }

    @Test
    void testIsEmailVerificationTokenValidWhenTokenValid() {
        userLocalIdentity.setEmailVerificationToken("token123");
        userLocalIdentity.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(1));
        assertTrue(userLocalIdentity.isEmailVerificationTokenValid());
    }

    @Test
    void testIsPasswordResetTokenValidWhenTokenIsNull() {
        userLocalIdentity.setPasswordResetToken(null);
        userLocalIdentity.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        assertFalse(userLocalIdentity.isPasswordResetTokenValid());
    }

    @Test
    void testIsPasswordResetTokenValidWhenExpirationIsNull() {
        userLocalIdentity.setPasswordResetToken("resetToken123");
        userLocalIdentity.setPasswordResetExpiresAt(null);
        assertFalse(userLocalIdentity.isPasswordResetTokenValid());
    }

    @Test
    void testIsPasswordResetTokenValidWhenTokenExpired() {
        userLocalIdentity.setPasswordResetToken("resetToken123");
        userLocalIdentity.setPasswordResetExpiresAt(LocalDateTime.now().minusHours(1));
        assertFalse(userLocalIdentity.isPasswordResetTokenValid());
    }

    @Test
    void testIsPasswordResetTokenValidWhenTokenValid() {
        userLocalIdentity.setPasswordResetToken("resetToken123");
        userLocalIdentity.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        assertTrue(userLocalIdentity.isPasswordResetTokenValid());
    }

    @Test
    void testGettersAndSetters() {
        UUID testId = UUID.randomUUID();
        String testEmail = "newtest@example.com";
        String testPasswordHash = "$2a$10$newHashedPassword";
        String testSalt = "saltValue";
        Boolean testEmailVerified = true;
        String testVerificationToken = "verificationToken123";
        LocalDateTime testVerificationExpiry = LocalDateTime.now().plusHours(24);
        String testResetToken = "resetToken123";
        LocalDateTime testResetExpiry = LocalDateTime.now().plusHours(1);
        Integer testFailedAttempts = 3;
        LocalDateTime testLockedUntil = LocalDateTime.now().plusMinutes(30);
        LocalDateTime testLastPasswordChange = LocalDateTime.now().minusDays(30);
        LocalDateTime testCreatedAt = LocalDateTime.now().minusDays(1);
        LocalDateTime testUpdatedAt = LocalDateTime.now();

        userLocalIdentity.setId(testId);
        userLocalIdentity.setEmail(testEmail);
        userLocalIdentity.setPasswordHash(testPasswordHash);
        userLocalIdentity.setSalt(testSalt);
        userLocalIdentity.setIsEmailVerified(testEmailVerified);
        userLocalIdentity.setEmailVerificationToken(testVerificationToken);
        userLocalIdentity.setEmailVerificationExpiresAt(testVerificationExpiry);
        userLocalIdentity.setPasswordResetToken(testResetToken);
        userLocalIdentity.setPasswordResetExpiresAt(testResetExpiry);
        userLocalIdentity.setFailedLoginAttempts(testFailedAttempts);
        userLocalIdentity.setLockedUntil(testLockedUntil);
        userLocalIdentity.setLastPasswordChangeAt(testLastPasswordChange);
        userLocalIdentity.setCreatedAt(testCreatedAt);
        userLocalIdentity.setUpdatedAt(testUpdatedAt);

        assertEquals(testId, userLocalIdentity.getId());
        assertEquals(testEmail, userLocalIdentity.getEmail());
        assertEquals(testPasswordHash, userLocalIdentity.getPasswordHash());
        assertEquals(testSalt, userLocalIdentity.getSalt());
        assertEquals(testEmailVerified, userLocalIdentity.getIsEmailVerified());
        assertEquals(testVerificationToken, userLocalIdentity.getEmailVerificationToken());
        assertEquals(testVerificationExpiry, userLocalIdentity.getEmailVerificationExpiresAt());
        assertEquals(testResetToken, userLocalIdentity.getPasswordResetToken());
        assertEquals(testResetExpiry, userLocalIdentity.getPasswordResetExpiresAt());
        assertEquals(testFailedAttempts, userLocalIdentity.getFailedLoginAttempts());
        assertEquals(testLockedUntil, userLocalIdentity.getLockedUntil());
        assertEquals(testLastPasswordChange, userLocalIdentity.getLastPasswordChangeAt());
        assertEquals(testCreatedAt, userLocalIdentity.getCreatedAt());
        assertEquals(testUpdatedAt, userLocalIdentity.getUpdatedAt());
    }

    @Test
    void testConstructors() {
        // Test no-args constructor
        UserLocalIdentity noArgsIdentity = new UserLocalIdentity();
        assertNotNull(noArgsIdentity);

        // Test all-args constructor
        UUID testId = UUID.randomUUID();
        String testEmail = "constructor@example.com";
        String testPasswordHash = "$2a$10$constructorHash";
        String testSalt = "constructorSalt";
        Boolean testEmailVerified = true;
        String testVerificationToken = "constructorVerificationToken";
        LocalDateTime testVerificationExpiry = LocalDateTime.now().plusHours(24);
        String testResetToken = "constructorResetToken";
        LocalDateTime testResetExpiry = LocalDateTime.now().plusHours(1);
        Integer testFailedAttempts = 0;
        LocalDateTime testLockedUntil = null;
        LocalDateTime testLastPasswordChange = LocalDateTime.now();
        LocalDateTime testCreatedAt = LocalDateTime.now();
        LocalDateTime testUpdatedAt = LocalDateTime.now();

        UserLocalIdentity allArgsIdentity = new UserLocalIdentity(
            testId, user, testEmail, testPasswordHash, testSalt, testEmailVerified, false,
            testVerificationToken, testVerificationExpiry, testResetToken, testResetExpiry,
            testFailedAttempts, testLockedUntil, testLastPasswordChange, testCreatedAt, testUpdatedAt
        );

        assertEquals(testId, allArgsIdentity.getId());
        assertEquals(user, allArgsIdentity.getUser());
        assertEquals(testEmail, allArgsIdentity.getEmail());
        assertEquals(testPasswordHash, allArgsIdentity.getPasswordHash());
        assertEquals(testSalt, allArgsIdentity.getSalt());
        assertEquals(testEmailVerified, allArgsIdentity.getIsEmailVerified());
        assertEquals(false, allArgsIdentity.getIsOAuthPlaceholder());
        assertEquals(testVerificationToken, allArgsIdentity.getEmailVerificationToken());
        assertEquals(testVerificationExpiry, allArgsIdentity.getEmailVerificationExpiresAt());
        assertEquals(testResetToken, allArgsIdentity.getPasswordResetToken());
        assertEquals(testResetExpiry, allArgsIdentity.getPasswordResetExpiresAt());
        assertEquals(testFailedAttempts, allArgsIdentity.getFailedLoginAttempts());
        assertEquals(testLockedUntil, allArgsIdentity.getLockedUntil());
        assertEquals(testLastPasswordChange, allArgsIdentity.getLastPasswordChangeAt());
        assertEquals(testCreatedAt, allArgsIdentity.getCreatedAt());
        assertEquals(testUpdatedAt, allArgsIdentity.getUpdatedAt());
    }
}