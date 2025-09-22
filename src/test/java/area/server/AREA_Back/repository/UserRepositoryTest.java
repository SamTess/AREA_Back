package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
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
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setEnabled(true);
    }

    @Test
    void shouldFindUserByEmail() {
        // Given
        entityManager.persistAndFlush(testUser);

        // When
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldFindUserByUsername() {
        // Given
        entityManager.persistAndFlush(testUser);

        // When
        Optional<User> found = userRepository.findByUsername("testuser");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void shouldReturnTrueWhenUserExistsByEmail() {
        // Given
        entityManager.persistAndFlush(testUser);

        // When
        boolean exists = userRepository.existsByEmail("test@example.com");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserDoesNotExistByEmail() {
        // When
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUserExistsByUsername() {
        // Given
        entityManager.persistAndFlush(testUser);

        // When
        boolean exists = userRepository.existsByUsername("testuser");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldFindAllEnabledUsers() {
        // Given
        User disabledUser = new User();
        disabledUser.setEmail("disabled@example.com");
        disabledUser.setUsername("disableduser");
        disabledUser.setPassword("password123");
        disabledUser.setEnabled(false);

        entityManager.persistAndFlush(testUser);
        entityManager.persistAndFlush(disabledUser);

        // When
        List<User> enabledUsers = userRepository.findAllEnabledUsers();

        // Then
        assertThat(enabledUsers).hasSize(1);
        assertThat(enabledUsers.get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldFindUserByEmailOrUsername() {
        // Given
        entityManager.persistAndFlush(testUser);

        // When
        Optional<User> foundByEmail = userRepository.findByEmailOrUsername("test@example.com");
        Optional<User> foundByUsername = userRepository.findByEmailOrUsername("testuser");

        // Then
        assertThat(foundByEmail).isPresent();
        assertThat(foundByUsername).isPresent();
        assertThat(foundByEmail.get().getId()).isEqualTo(foundByUsername.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenUserNotFoundByEmailOrUsername() {
        // When
        Optional<User> found = userRepository.findByEmailOrUsername("nonexistent");

        // Then
        assertThat(found).isEmpty();
    }
}