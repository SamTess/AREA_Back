package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private SimpleMeterRegistry meterRegistry;
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        customUserDetailsService = new CustomUserDetailsService(userRepository, meterRegistry);
        // Manually initialize metrics since @PostConstruct won't run in tests
        customUserDetailsService.initMetrics();
    }

    @Test
    void testLoadUserByUsernameWithValidUser() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = userId.toString();

        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setIsActive(true);
        user.setIsAdmin(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        var userDetails = customUserDetailsService.loadUserByUsername(username);

        // Then
        assertNotNull(userDetails);
        assertEquals(username, userDetails.getUsername());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isCredentialsNonExpired());
        assertEquals(1, userDetails.getAuthorities().size());
        assertTrue(userDetails.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testLoadUserByUsernameWithAdminUser() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = userId.toString();

        User user = new User();
        user.setId(userId);
        user.setEmail("admin@example.com");
        user.setIsActive(true);
        user.setIsAdmin(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        var userDetails = customUserDetailsService.loadUserByUsername(username);

        // Then
        assertNotNull(userDetails);
        assertEquals(username, userDetails.getUsername());
        assertTrue(userDetails.isEnabled());
        assertEquals(2, userDetails.getAuthorities().size());
        assertTrue(userDetails.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(userDetails.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testLoadUserByUsernameWithInactiveUser() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = userId.toString();

        User user = new User();
        user.setId(userId);
        user.setEmail("inactive@example.com");
        user.setIsActive(false);
        user.setIsAdmin(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When & Then
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername(username);
        });
        assertEquals("User account is inactive: " + userId, exception.getMessage());
    }

    @Test
    void testLoadUserByUsernameWithNonExistentUser() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = userId.toString();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername(username);
        });
        assertEquals("User not found with ID: " + userId, exception.getMessage());
    }

    @Test
    void testLoadUserByUsernameWithInvalidUUID() {
        // Given
        String invalidUsername = "invalid-uuid";

        // When & Then
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername(invalidUsername);
        });
        assertEquals("Invalid user ID format: invalid-uuid", exception.getMessage());
    }

    @Test
    void testCustomUserPrincipal() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setIsActive(true);
        user.setIsAdmin(true);

        CustomUserDetailsService.CustomUserPrincipal principal = new CustomUserDetailsService.CustomUserPrincipal(user);

        // When & Then
        assertEquals(userId.toString(), principal.getUsername());
        assertEquals("", principal.getPassword());
        assertTrue(principal.isEnabled());
        assertTrue(principal.isAccountNonExpired());
        assertTrue(principal.isAccountNonLocked());
        assertTrue(principal.isCredentialsNonExpired());
        assertEquals(user, principal.getUser());
        assertEquals(userId, principal.getUserId());
        assertEquals("test@example.com", principal.getEmail());
        assertTrue(principal.isAdmin());
    }
}