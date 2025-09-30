package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(locations = "classpath:application-repository-test.properties")
@Transactional
class UserLocalIdentityRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserLocalIdentityRepository userLocalIdentityRepository;

    private User testUser;
    private UserLocalIdentity testUserLocalIdentity;

    @BeforeEach
    void setUp() {
        // Create and persist test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedPassword123");
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setAvatarUrl("https://example.com/avatar.jpg");

        testUser = entityManager.persistAndFlush(testUser);

        // Create and persist test user local identity
        testUserLocalIdentity = new UserLocalIdentity();
        testUserLocalIdentity.setUser(testUser);
        testUserLocalIdentity.setEmail("test@example.com");
        testUserLocalIdentity.setPasswordHash("$2a$10$hashedPassword");
        testUserLocalIdentity.setIsEmailVerified(false);
        testUserLocalIdentity.setFailedLoginAttempts(0);
        testUserLocalIdentity.setCreatedAt(LocalDateTime.now());
        testUserLocalIdentity.setUpdatedAt(LocalDateTime.now());

        testUserLocalIdentity = entityManager.persistAndFlush(testUserLocalIdentity);
    }

    @Test
    void testFindByEmail() {
        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository.findByEmail("test@example.com");

        assertTrue(foundIdentity.isPresent());
        assertEquals(testUserLocalIdentity.getId(), foundIdentity.get().getId());
        assertEquals("test@example.com", foundIdentity.get().getEmail());
        assertEquals(testUser.getId(), foundIdentity.get().getUser().getId());
    }

    @Test
    void testFindByEmail_NotFound() {
        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository.findByEmail("nonexistent@example.com");

        assertFalse(foundIdentity.isPresent());
    }

    @Test
    void testFindByUser() {
        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository.findByUser(testUser);

        assertTrue(foundIdentity.isPresent());
        assertEquals(testUserLocalIdentity.getId(), foundIdentity.get().getId());
        assertEquals(testUser.getId(), foundIdentity.get().getUser().getId());
    }

    @Test
    void testFindByUser_NotFound() {
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        anotherUser.setPasswordHash("hashedPassword123");
        anotherUser.setIsActive(true);
        anotherUser.setIsAdmin(false);
        anotherUser.setCreatedAt(LocalDateTime.now());
        anotherUser = entityManager.persistAndFlush(anotherUser);

        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository.findByUser(anotherUser);

        assertFalse(foundIdentity.isPresent());
    }

    @Test
    void testFindByUserId() {
        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository.findByUserId(testUser.getId());

        assertTrue(foundIdentity.isPresent());
        assertEquals(testUserLocalIdentity.getId(), foundIdentity.get().getId());
        assertEquals(testUser.getId(), foundIdentity.get().getUser().getId());
    }

    @Test
    void testFindByUserId_NotFound() {
        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository.findByUserId(UUID.randomUUID());

        assertFalse(foundIdentity.isPresent());
    }

    @Test
    void testExistsByEmail() {
        assertTrue(userLocalIdentityRepository.existsByEmail("test@example.com"));
        assertFalse(userLocalIdentityRepository.existsByEmail("nonexistent@example.com"));
    }

    @Test
    void testFindByEmailVerificationToken() {
        // Set up test data with verification token
        testUserLocalIdentity.setEmailVerificationToken("verification123");
        testUserLocalIdentity.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(24));
        entityManager.persistAndFlush(testUserLocalIdentity);

        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository
            .findByEmailVerificationToken("verification123");

        assertTrue(foundIdentity.isPresent());
        assertEquals(testUserLocalIdentity.getId(), foundIdentity.get().getId());
        assertEquals("verification123", foundIdentity.get().getEmailVerificationToken());
    }

    @Test
    void testFindByEmailVerificationToken_NotFound() {
        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository
            .findByEmailVerificationToken("nonexistent");

        assertFalse(foundIdentity.isPresent());
    }

    @Test
    void testFindByPasswordResetToken() {
        // Set up test data with reset token
        testUserLocalIdentity.setPasswordResetToken("reset123");
        testUserLocalIdentity.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        entityManager.persistAndFlush(testUserLocalIdentity);

        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository
            .findByPasswordResetToken("reset123");

        assertTrue(foundIdentity.isPresent());
        assertEquals(testUserLocalIdentity.getId(), foundIdentity.get().getId());
        assertEquals("reset123", foundIdentity.get().getPasswordResetToken());
    }

    @Test
    void testFindByPasswordResetToken_NotFound() {
        Optional<UserLocalIdentity> foundIdentity = userLocalIdentityRepository
            .findByPasswordResetToken("nonexistent");

        assertFalse(foundIdentity.isPresent());
    }

    @Test
    void testIncrementFailedLoginAttempts() {
        assertEquals(0, testUserLocalIdentity.getFailedLoginAttempts());

        userLocalIdentityRepository.incrementFailedLoginAttempts("test@example.com");
        entityManager.flush();
        entityManager.clear();

        UserLocalIdentity updatedIdentity = userLocalIdentityRepository
            .findByEmail("test@example.com").orElse(null);

        assertNotNull(updatedIdentity);
        assertEquals(1, updatedIdentity.getFailedLoginAttempts());
    }

    @Test
    void testResetFailedLoginAttempts() {
        // First, set some failed attempts
        testUserLocalIdentity.setFailedLoginAttempts(5);
        testUserLocalIdentity.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        entityManager.persistAndFlush(testUserLocalIdentity);

        userLocalIdentityRepository.resetFailedLoginAttempts("test@example.com");
        entityManager.flush();
        entityManager.clear();

        UserLocalIdentity updatedIdentity = userLocalIdentityRepository
            .findByEmail("test@example.com").orElse(null);

        assertNotNull(updatedIdentity);
        assertEquals(0, updatedIdentity.getFailedLoginAttempts());
        assertNull(updatedIdentity.getLockedUntil());
    }

    @Test
    void testLockAccount() {
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(30);

        userLocalIdentityRepository.lockAccount("test@example.com", lockUntil);
        entityManager.flush();
        entityManager.clear();

        UserLocalIdentity updatedIdentity = userLocalIdentityRepository
            .findByEmail("test@example.com").orElse(null);

        assertNotNull(updatedIdentity);
        assertNotNull(updatedIdentity.getLockedUntil());
        // Allow some tolerance for time differences
        assertTrue(updatedIdentity.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(25)));
        assertTrue(updatedIdentity.getLockedUntil().isBefore(LocalDateTime.now().plusMinutes(35)));
    }

    @Test
    void testClearEmailVerificationToken() {
        // Set up test data with verification token
        testUserLocalIdentity.setEmailVerificationToken("verification123");
        testUserLocalIdentity.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(24));
        testUserLocalIdentity.setIsEmailVerified(false);
        entityManager.persistAndFlush(testUserLocalIdentity);

        userLocalIdentityRepository.clearEmailVerificationToken("verification123");
        entityManager.flush();
        entityManager.clear();

        UserLocalIdentity updatedIdentity = userLocalIdentityRepository
            .findByEmail("test@example.com").orElse(null);

        assertNotNull(updatedIdentity);
        assertNull(updatedIdentity.getEmailVerificationToken());
        assertNull(updatedIdentity.getEmailVerificationExpiresAt());
        assertTrue(updatedIdentity.getIsEmailVerified());
    }

    @Test
    void testClearPasswordResetToken() {
        // Set up test data with reset token
        testUserLocalIdentity.setPasswordResetToken("reset123");
        testUserLocalIdentity.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        entityManager.persistAndFlush(testUserLocalIdentity);

        userLocalIdentityRepository.clearPasswordResetToken("reset123");
        entityManager.flush();
        entityManager.clear();

        UserLocalIdentity updatedIdentity = userLocalIdentityRepository
            .findByEmail("test@example.com").orElse(null);

        assertNotNull(updatedIdentity);
        assertNull(updatedIdentity.getPasswordResetToken());
        assertNull(updatedIdentity.getPasswordResetExpiresAt());
    }

    @Test
    void testSaveUserLocalIdentity() {
        UserLocalIdentity newIdentity = new UserLocalIdentity();
        newIdentity.setUser(testUser);
        newIdentity.setEmail("new@example.com");
        newIdentity.setPasswordHash("$2a$10$newHashedPassword");
        newIdentity.setIsEmailVerified(false);
        newIdentity.setFailedLoginAttempts(0);
        newIdentity.setCreatedAt(LocalDateTime.now());
        newIdentity.setUpdatedAt(LocalDateTime.now());

        UserLocalIdentity savedIdentity = userLocalIdentityRepository.save(newIdentity);

        assertNotNull(savedIdentity.getId());
        assertEquals("new@example.com", savedIdentity.getEmail());
        assertEquals(testUser.getId(), savedIdentity.getUser().getId());
    }

    @Test
    void testDeleteUserLocalIdentity() {
        UUID identityId = testUserLocalIdentity.getId();

        userLocalIdentityRepository.delete(testUserLocalIdentity);
        entityManager.flush();

        Optional<UserLocalIdentity> deletedIdentity = userLocalIdentityRepository.findById(identityId);
        assertFalse(deletedIdentity.isPresent());
    }

    @Test
    void testFindAll() {
        // Create another user and identity
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        anotherUser.setPasswordHash("hashedPassword123");
        anotherUser.setIsActive(true);
        anotherUser.setIsAdmin(false);
        anotherUser.setCreatedAt(LocalDateTime.now());
        anotherUser = entityManager.persistAndFlush(anotherUser);

        UserLocalIdentity anotherIdentity = new UserLocalIdentity();
        anotherIdentity.setUser(anotherUser);
        anotherIdentity.setEmail("another@example.com");
        anotherIdentity.setPasswordHash("$2a$10$anotherHashedPassword");
        anotherIdentity.setIsEmailVerified(true);
        anotherIdentity.setFailedLoginAttempts(0);
        anotherIdentity.setCreatedAt(LocalDateTime.now());
        anotherIdentity.setUpdatedAt(LocalDateTime.now());
        entityManager.persistAndFlush(anotherIdentity);

        var allIdentities = userLocalIdentityRepository.findAll();

        assertEquals(2, allIdentities.size());
        assertTrue(allIdentities.stream().anyMatch(identity ->
            identity.getEmail().equals("test@example.com")));
        assertTrue(allIdentities.stream().anyMatch(identity ->
            identity.getEmail().equals("another@example.com")));
    }
}