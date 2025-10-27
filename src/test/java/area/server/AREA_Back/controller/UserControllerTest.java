package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.util.AuthenticationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLocalIdentityRepository localIdentityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserController userController;

    private User testUser;
    private UUID userId;
    private MockedStatic<AuthenticationUtils> authUtils;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        
        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");
        testUser.setFirstname("John");
        testUser.setLastname("Doe");
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setLastLoginAt(LocalDateTime.now());
        testUser.setAvatarUrl("https://example.com/avatar.jpg");
        
        // Setup AuthenticationUtils mock
        authUtils = mockStatic(AuthenticationUtils.class);
    }

    @AfterEach
    void tearDown() {
        if (authUtils != null) {
            authUtils.close();
        }
    }

    // Tests for getAllUsers
    @Test
    void getAllUsersShouldReturnPaginatedUsersWithDefaultParameters() {
        // Given
        List<User> users = Arrays.asList(testUser);
        Page<User> userPage = new PageImpl<>(users);
        
        when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);

        // When
        ResponseEntity<Page<UserResponse>> response = userController.getAllUsers(0, 20, "id", "asc");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().get(0).getEmail()).isEqualTo("test@example.com");
        
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    void getAllUsersShouldReturnPaginatedUsersWithDescendingSort() {
        // Given
        List<User> users = Arrays.asList(testUser);
        Page<User> userPage = new PageImpl<>(users);
        
        when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);

        // When
        ResponseEntity<Page<UserResponse>> response = userController.getAllUsers(0, 20, "email", "desc");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    void getAllUsersShouldReturnEmptyPageWhenNoUsers() {
        // Given
        Page<User> emptyPage = new PageImpl<>(Arrays.asList());
        when(userRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        // When
        ResponseEntity<Page<UserResponse>> response = userController.getAllUsers(0, 20, "id", "asc");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
    }

    @Test
    void getAllUsersShouldHandleCustomPageSizeAndNumber() {
        // Given
        User user2 = new User();
        user2.setId(UUID.randomUUID());
        user2.setEmail("test2@example.com");
        user2.setFirstname("Jane");
        user2.setLastname("Smith");
        user2.setIsActive(true);
        user2.setIsAdmin(false);
        user2.setCreatedAt(LocalDateTime.now());
        
        List<User> users = Arrays.asList(testUser, user2);
        Page<User> userPage = new PageImpl<>(users);
        
        when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);

        // When
        ResponseEntity<Page<UserResponse>> response = userController.getAllUsers(1, 10, "firstname", "asc");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(2);
    }

    // Tests for getUserById
    @Test
    void getUserByIdShouldReturnUserWhenFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        ResponseEntity<UserResponse> response = userController.getUserById(userId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(userId);
        assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBody().getFirstname()).isEqualTo("John");
        assertThat(response.getBody().getLastname()).isEqualTo("Doe");
        
        verify(userRepository).findById(userId);
    }

    @Test
    void getUserByIdShouldReturnNotFoundWhenUserDoesNotExist() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<UserResponse> response = userController.getUserById(userId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
        
        verify(userRepository).findById(userId);
    }

    // Tests for getEnabledUsers
    @Test
    void getEnabledUsersShouldReturnListOfEnabledUsers() {
        // Given
        User enabledUser1 = new User();
        enabledUser1.setId(UUID.randomUUID());
        enabledUser1.setEmail("enabled1@example.com");
        enabledUser1.setFirstname("Enabled");
        enabledUser1.setLastname("One");
        enabledUser1.setIsActive(true);
        enabledUser1.setIsAdmin(false);
        enabledUser1.setCreatedAt(LocalDateTime.now());
        
        User enabledUser2 = new User();
        enabledUser2.setId(UUID.randomUUID());
        enabledUser2.setEmail("enabled2@example.com");
        enabledUser2.setFirstname("Enabled");
        enabledUser2.setLastname("Two");
        enabledUser2.setIsActive(true);
        enabledUser2.setIsAdmin(false);
        enabledUser2.setCreatedAt(LocalDateTime.now());
        
        List<User> enabledUsers = Arrays.asList(enabledUser1, enabledUser2);
        when(userRepository.findAllEnabledUsers()).thenReturn(enabledUsers);

        // When
        ResponseEntity<List<UserResponse>> response = userController.getEnabledUsers();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getEmail()).isEqualTo("enabled1@example.com");
        assertThat(response.getBody().get(1).getEmail()).isEqualTo("enabled2@example.com");
        
        verify(userRepository).findAllEnabledUsers();
    }

    @Test
    void getEnabledUsersShouldReturnEmptyListWhenNoEnabledUsers() {
        // Given
        when(userRepository.findAllEnabledUsers()).thenReturn(Arrays.asList());

        // When
        ResponseEntity<List<UserResponse>> response = userController.getEnabledUsers();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
        
        verify(userRepository).findAllEnabledUsers();
    }

    // Tests for deleteUser
    @Test
    void deleteUserShouldDeleteUserWhenExists() {
        // Given
        authUtils.when(AuthenticationUtils::getCurrentUserId).thenReturn(userId);
        authUtils.when(AuthenticationUtils::isCurrentUserAdmin).thenReturn(false);
        when(userRepository.existsById(userId)).thenReturn(true);

        // When
        ResponseEntity<?> response = userController.deleteUser(userId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        
        verify(userRepository).existsById(userId);
        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteUserShouldReturnNotFoundWhenUserDoesNotExist() {
        // Given
        authUtils.when(AuthenticationUtils::getCurrentUserId).thenReturn(userId);
        authUtils.when(AuthenticationUtils::isCurrentUserAdmin).thenReturn(false);
        when(userRepository.existsById(userId)).thenReturn(false);

        // When
        ResponseEntity<?> response = userController.deleteUser(userId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        
        verify(userRepository).existsById(userId);
        verify(userRepository, never()).deleteById(any(UUID.class));
    }

    // Tests for searchUsers
    @Test
    void searchUsersShouldReturnUserWhenFound() {
        // Given
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // When
        ResponseEntity<List<UserResponse>> response = userController.searchUsers(email);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getEmail()).isEqualTo(email);
        
        verify(userRepository).findByEmail(email);
    }

    @Test
    void searchUsersShouldReturnEmptyListWhenUserNotFound() {
        // Given
        String email = "nonexistent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When
        ResponseEntity<List<UserResponse>> response = userController.searchUsers(email);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
        
        verify(userRepository).findByEmail(email);
    }

    // Additional tests to ensure UserResponse conversion is properly covered
    @Test
    void getUserByIdShouldReturnCompleteUserResponse() {
        // Given
        testUser.setLastLoginAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        ResponseEntity<UserResponse> response = userController.getUserById(userId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse userResponse = response.getBody();
        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getId()).isEqualTo(userId);
        assertThat(userResponse.getEmail()).isEqualTo("test@example.com");
        assertThat(userResponse.getFirstname()).isEqualTo("John");
        assertThat(userResponse.getLastname()).isEqualTo("Doe");
        assertThat(userResponse.getIsActive()).isTrue();
        assertThat(userResponse.getIsAdmin()).isFalse();
        assertThat(userResponse.getCreatedAt()).isNotNull();
        assertThat(userResponse.getLastLoginAt()).isNotNull();
        assertThat(userResponse.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");
    }
}
