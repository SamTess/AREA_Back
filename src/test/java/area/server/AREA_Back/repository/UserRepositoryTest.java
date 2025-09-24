package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(locations = "classpath:application-repository-test.properties")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedPassword123");
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setAvatarUrl("https://example.com/avatar.jpg");
        
        entityManager.persistAndFlush(testUser);
    }

    @Test
    void testFindByEmail() {
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");
        
        assertTrue(foundUser.isPresent());
        assertEquals("test@example.com", foundUser.get().getEmail());
        assertEquals("hashedPassword123", foundUser.get().getPasswordHash());
    }

    @Test
    void testFindByEmailNotFound() {
        Optional<User> foundUser = userRepository.findByEmail("notfound@example.com");
        
        assertFalse(foundUser.isPresent());
    }

    @Test
    void testExistsByEmail() {
        boolean exists = userRepository.existsByEmail("test@example.com");
        assertTrue(exists);
        
        boolean notExists = userRepository.existsByEmail("notfound@example.com");
        assertFalse(notExists);
    }

    @Test
    void testFindAllEnabledUsers() {
        // Create another active user
        User activeUser = new User();
        activeUser.setEmail("active@example.com");
        activeUser.setPasswordHash("hashedPassword456");
        activeUser.setIsActive(true);
        activeUser.setIsAdmin(false);
        entityManager.persistAndFlush(activeUser);

        // Create an inactive user
        User inactiveUser = new User();
        inactiveUser.setEmail("inactive@example.com");
        inactiveUser.setPasswordHash("hashedPassword789");
        inactiveUser.setIsActive(false);
        inactiveUser.setIsAdmin(false);
        entityManager.persistAndFlush(inactiveUser);

        List<User> enabledUsers = userRepository.findAllEnabledUsers();
        
        assertEquals(2, enabledUsers.size());
        assertTrue(enabledUsers.stream().allMatch(User::getIsActive));
        assertTrue(enabledUsers.stream().anyMatch(u -> u.getEmail().equals("test@example.com")));
        assertTrue(enabledUsers.stream().anyMatch(u -> u.getEmail().equals("active@example.com")));
        assertFalse(enabledUsers.stream().anyMatch(u -> u.getEmail().equals("inactive@example.com")));
    }

    @Test
    void testSaveUser() {
        User newUser = new User();
        newUser.setEmail("new@example.com");
        newUser.setPasswordHash("newHashedPassword");
        newUser.setIsActive(true);
        newUser.setIsAdmin(false);
        newUser.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(newUser);
        
        assertNotNull(savedUser.getId());
        assertEquals("new@example.com", savedUser.getEmail());
        assertEquals("newHashedPassword", savedUser.getPasswordHash());
        assertTrue(savedUser.getIsActive());
        assertFalse(savedUser.getIsAdmin());
    }

    @Test
    void testUpdateUser() {
        testUser.setPasswordHash("updatedPassword");
        testUser.setIsAdmin(true);
        
        User updatedUser = userRepository.save(testUser);
        
        assertEquals(testUser.getId(), updatedUser.getId());
        assertEquals("updatedPassword", updatedUser.getPasswordHash());
        assertTrue(updatedUser.getIsAdmin());
    }

    @Test
    void testDeleteUser() {
        UUID userId = testUser.getId();
        
        userRepository.delete(testUser);
        
        Optional<User> deletedUser = userRepository.findById(userId);
        assertFalse(deletedUser.isPresent());
    }

    @Test
    void testFindById() {
        Optional<User> foundUser = userRepository.findById(testUser.getId());
        
        assertTrue(foundUser.isPresent());
        assertEquals(testUser.getId(), foundUser.get().getId());
        assertEquals("test@example.com", foundUser.get().getEmail());
    }

    @Test
    void testFindAll() {
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        anotherUser.setPasswordHash("anotherPassword");
        anotherUser.setIsActive(true);
        anotherUser.setIsAdmin(false);
        entityManager.persistAndFlush(anotherUser);

        List<User> allUsers = userRepository.findAll();
        
        assertEquals(2, allUsers.size());
    }

    @Test
    void testExistsById() {
        boolean exists = userRepository.existsById(testUser.getId());
        assertTrue(exists);
        
        boolean notExists = userRepository.existsById(UUID.randomUUID());
        assertFalse(notExists);
    }

    @Test
    void testCount() {
        long count = userRepository.count();
        assertEquals(1, count);
        
        User anotherUser = new User();
        anotherUser.setEmail("count@example.com");
        anotherUser.setPasswordHash("countPassword");
        anotherUser.setIsActive(true);
        anotherUser.setIsAdmin(false);
        entityManager.persistAndFlush(anotherUser);
        
        long newCount = userRepository.count();
        assertEquals(2, newCount);
    }
}