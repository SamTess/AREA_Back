package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.UpdateUserRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserControllerUpdateTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLocalIdentityRepository localIdentityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserController userController;

    private User testUser;
    private UserLocalIdentity testIdentity;
    private UUID userId;

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
        
        testIdentity = new UserLocalIdentity();
        testIdentity.setId(UUID.randomUUID());
        testIdentity.setUser(testUser);
        testIdentity.setEmail("test@example.com");
        testIdentity.setPasswordHash("hashedPassword");
    }

    @Test
    void updateUserShouldUpdateFirstnameAndLastname() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstname("Jane");
        request.setLastname("Smith");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<?> response = userController.updateUser(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(UserResponse.class);
        
        UserResponse userResponse = (UserResponse) response.getBody();
        assertThat(userResponse.getFirstname()).isEqualTo("Jane");
        assertThat(userResponse.getLastname()).isEqualTo("Smith");
        
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUserShouldUpdatePassword() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("newPassword123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(localIdentityRepository.findByUserId(userId)).thenReturn(Optional.of(testIdentity));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<?> response = userController.updateUser(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(passwordEncoder).encode("newPassword123");
        verify(localIdentityRepository).save(any(UserLocalIdentity.class));
    }

    @Test
    void updateUserShouldUpdateEmailInBothUserAndLocalIdentity() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("newemail@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("newemail@example.com")).thenReturn(Optional.empty());
        when(localIdentityRepository.findByUserId(userId)).thenReturn(Optional.of(testIdentity));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<?> response = userController.updateUser(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testUser.getEmail()).isEqualTo("newemail@example.com");
        assertThat(testIdentity.getEmail()).isEqualTo("newemail@example.com");
        verify(userRepository).save(any(User.class));
        verify(localIdentityRepository).save(any(UserLocalIdentity.class));
    }


    @Test
    void updateUserShouldUpdateAllFieldsTogether() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstname("Jane");
        request.setLastname("Smith");
        request.setEmail("newemail@example.com");
        request.setPassword("newPassword123");
        request.setAvatarUrl("https://example.com/avatar.jpg");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("newemail@example.com")).thenReturn(Optional.empty());
        when(localIdentityRepository.findByUserId(userId)).thenReturn(Optional.of(testIdentity));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<?> response = userController.updateUser(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userRepository).save(any(User.class));
        // LocalIdentity is saved twice: once for email update, once for password update
        verify(localIdentityRepository, org.mockito.Mockito.times(2)).save(any(UserLocalIdentity.class));
        verify(passwordEncoder).encode("newPassword123");
    }

    @Test
    void updateUserShouldReturnNotFoundWhenUserDoesNotExist() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstname("Jane");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<?> response = userController.updateUser(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserShouldReturnBadRequestWhenEmailAlreadyExists() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("existing@example.com");

        User anotherUser = new User();
        anotherUser.setId(UUID.randomUUID());
        anotherUser.setEmail("existing@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(anotherUser));

        // When
        ResponseEntity<?> response = userController.updateUser(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("error", "Email already exists"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserShouldNotUpdatePasswordWhenNoLocalIdentity() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("newPassword123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(localIdentityRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<?> response = userController.updateUser(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(passwordEncoder, never()).encode(anyString());
        verify(localIdentityRepository, never()).save(any(UserLocalIdentity.class));
    }

    @Test
    void updateUserShouldOnlyUpdateProvidedFields() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstname("Jane");
        // Only firstname is set, other fields should remain unchanged

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        ResponseEntity<?> response = userController.updateUser(userId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse userResponse = (UserResponse) response.getBody();
        assertThat(userResponse.getFirstname()).isEqualTo("Jane");
        assertThat(userResponse.getEmail()).isEqualTo("test@example.com");
        verify(passwordEncoder, never()).encode(anyString());
    }
}
