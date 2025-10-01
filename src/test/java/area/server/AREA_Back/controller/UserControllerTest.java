package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.CreateUserRequest;
import area.server.AREA_Back.dto.UpdateUserRequest;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@WithMockUser
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private CreateUserRequest createUserRequest;
    private UpdateUserRequest updateUserRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setLastLoginAt(LocalDateTime.now());
        testUser.setAvatarUrl("https://example.com/avatar.png");

        createUserRequest = new CreateUserRequest();
        createUserRequest.setEmail("newuser@example.com");
        createUserRequest.setPassword("password123");

        updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setEmail("updated@example.com");
        updateUserRequest.setIsActive(false);
    }

    @Test
    void testGetAllUsers() throws Exception {
        Page<User> userPage = new PageImpl<>(Arrays.asList(testUser));
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(userPage);

        mockMvc.perform(get("/api/users")
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "id")
                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email").value("test@example.com"));

        verify(userRepository).findAll(any(PageRequest.class));
    }

    @Test
    void testGetUserById() throws Exception {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/api/users/{id}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(userRepository).findById(testUser.getId());
    }

    @Test
    void testGetUserByIdNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/{id}", nonExistentId))
                .andExpect(status().isNotFound());

        verify(userRepository).findById(nonExistentId);
    }

    @Test
    void testCreateUser() throws Exception {
        User newUser = new User();
        newUser.setId(UUID.randomUUID());
        newUser.setEmail(createUserRequest.getEmail());
        newUser.setIsActive(true);
        newUser.setIsAdmin(false);
        newUser.setCreatedAt(LocalDateTime.now());

        when(userRepository.existsByEmail(createUserRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(createUserRequest.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(createUserRequest))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@example.com"));

        verify(userRepository).existsByEmail(createUserRequest.getEmail());
        verify(passwordEncoder).encode(createUserRequest.getPassword());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testCreateUserWithExistingEmail() throws Exception {
        when(userRepository.existsByEmail(createUserRequest.getEmail())).thenReturn(true);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isConflict());

        verify(userRepository).existsByEmail(createUserRequest.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testCreateUserWithInvalidData() throws Exception {
        CreateUserRequest invalidRequest = new CreateUserRequest();
        invalidRequest.setEmail("invalid-email");
        invalidRequest.setPassword("");

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUserWithNullPassword() throws Exception {
        CreateUserRequest requestWithNullPassword = new CreateUserRequest();
        requestWithNullPassword.setEmail("test@example.com");
        requestWithNullPassword.setPassword(null);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(requestWithNullPassword)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateUser() throws Exception {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        mockMvc.perform(put("/api/users/{id}", testUser.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateUserRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"));

        verify(userRepository).findById(testUser.getId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testUpdateUserNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/users/{id}", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(updateUserRequest)))
                .andExpect(status().isNotFound());

        verify(userRepository).findById(nonExistentId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testDeleteUser() throws Exception {
        when(userRepository.existsById(testUser.getId())).thenReturn(true);

        mockMvc.perform(delete("/api/users/{id}", testUser.getId())
                .with(csrf()))
                .andExpect(status().isNoContent());

        verify(userRepository).existsById(testUser.getId());
        verify(userRepository).deleteById(testUser.getId());
    }

    @Test
    void testDeleteUserNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.existsById(nonExistentId)).thenReturn(false);

        mockMvc.perform(delete("/api/users/{id}", nonExistentId)
                .with(csrf()))
                .andExpect(status().isNotFound());

        verify(userRepository).existsById(nonExistentId);
        verify(userRepository, never()).deleteById(nonExistentId);
    }

    @Test
    void testGetEnabledUsers() throws Exception {
        when(userRepository.findAllEnabledUsers()).thenReturn(Arrays.asList(testUser));

        mockMvc.perform(get("/api/users/enabled")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].email").value("test@example.com"));

        verify(userRepository).findAllEnabledUsers();
    }

    @Test
    void testGetAllUsersWithDefaultParameters() throws Exception {
        Page<User> userPage = new PageImpl<>(Arrays.asList(testUser));
        when(userRepository.findAll(PageRequest.of(0, 20, Sort.by("id").ascending()))).thenReturn(userPage);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(userRepository).findAll(PageRequest.of(0, 20, Sort.by("id").ascending()));
    }

    @Test
    void testGetAllUsersWithDescendingSort() throws Exception {
        Page<User> userPage = new PageImpl<>(Arrays.asList(testUser));
        when(userRepository.findAll(PageRequest.of(0, 20, Sort.by("id").descending()))).thenReturn(userPage);

        mockMvc.perform(get("/api/users")
                .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(userRepository).findAll(PageRequest.of(0, 20, Sort.by("id").descending()));
    }
}