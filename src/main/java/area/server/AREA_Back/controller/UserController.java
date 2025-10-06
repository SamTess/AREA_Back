package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.UpdateUserRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "API for managing users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Get all users",
               description = "Retrieves a paginated list of all users")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of users retrieved successfully")
    })
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @Parameter(description = "Page number (starts at 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction (asc or desc)")
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> users = userRepository.findAll(pageable);
        Page<UserResponse> userResponses = users.map(this::convertToResponse);

        return ResponseEntity.ok(userResponses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID",
               description = "Retrieves details of a specific user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User ID")
            @PathVariable UUID id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            return ResponseEntity.ok(convertToResponse(user.get()));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/enabled")
    @Operation(summary = "Get enabled users",
               description = "Retrieves the list of all enabled users")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of enabled users retrieved successfully")
    })
    public ResponseEntity<List<UserResponse>> getEnabledUsers() {
        List<User> enabledUsers = userRepository.findAllEnabledUsers();
        List<UserResponse> userResponses = enabledUsers.stream()
            .map(this::convertToResponse)
            .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(userResponses);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user",
               description = "Updates an existing user's information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User updated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "User ID")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        Optional<User> optionalUser = userRepository.findById(id);
        if (!optionalUser.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }
        if (request.getIsAdmin() != null) {
            user.setIsAdmin(request.getIsAdmin());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(convertToResponse(updatedUser));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user",
               description = "Deletes an existing user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User deleted successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User ID")
            @PathVariable UUID id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search for a user",
               description = "Searches for a user by email")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully")
    })
    public ResponseEntity<List<UserResponse>> searchUsers(
            @Parameter(description = "Email to search for")
            @RequestParam String email) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isPresent()) {
            return ResponseEntity.ok(List.of(convertToResponse(user.get())));
        }
        return ResponseEntity.ok(List.of());
    }

    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setIsActive(user.getIsActive());
        response.setIsAdmin(user.getIsAdmin());
        response.setCreatedAt(user.getCreatedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setAvatarUrl(user.getAvatarUrl());
        return response;
    }
}