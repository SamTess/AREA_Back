package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.CreateUserRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "API de gestion des utilisateurs")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    @Operation(summary = "Récupérer tous les utilisateurs",
               description = "Récupère une liste paginée de tous les utilisateurs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Liste des utilisateurs récupérée avec succès")
    })
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @Parameter(description = "Numéro de page (commence à 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Taille de la page")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Champ de tri")
            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Direction du tri (asc ou desc)")
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> users = userRepository.findAll(pageable);
        Page<UserResponse> userResponses = users.map(this::convertToResponse);

        return ResponseEntity.ok(userResponses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer un utilisateur par ID",
               description = "Récupère les détails d'un utilisateur spécifique")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Utilisateur trouvé"),
        @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé")
    })
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "ID de l'utilisateur")
            @PathVariable UUID id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            return ResponseEntity.ok(convertToResponse(user.get()));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/enabled")
    @Operation(summary = "Récupérer les utilisateurs activés",
               description = "Récupère la liste de tous les utilisateurs activés")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Liste des utilisateurs activés récupérée avec succès")
    })
    public ResponseEntity<List<UserResponse>> getEnabledUsers() {
        List<User> enabledUsers = userRepository.findAllEnabledUsers();
        List<UserResponse> userResponses = enabledUsers.stream()
            .map(this::convertToResponse)
            .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(userResponses);
    }

    @PostMapping
    @Operation(summary = "Créer un nouvel utilisateur",
               description = "Crée un nouvel utilisateur avec les informations fournies")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Utilisateur créé avec succès"),
        @ApiResponse(responseCode = "409", description = "Utilisateur existe déjà (email)")
    })
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        User user = new User();
        user.setEmail(request.getEmail());
        if (request.getPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        user.setAvatarUrl(request.getAvatarUrl());
        user.setIsActive(true);
        user.setIsAdmin(false);

        User savedUser = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(savedUser));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un utilisateur",
               description = "Met à jour les informations d'un utilisateur existant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Utilisateur mis à jour avec succès"),
        @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé")
    })
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "ID de l'utilisateur")
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
        if (request.getPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
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
    @Operation(summary = "Supprimer un utilisateur",
               description = "Supprime un utilisateur existant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Utilisateur supprimé avec succès"),
        @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé")
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "ID de l'utilisateur")
            @PathVariable UUID id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Rechercher un utilisateur",
               description = "Recherche un utilisateur par email")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Recherche effectuée avec succès")
    })
    public ResponseEntity<List<UserResponse>> searchUsers(
            @Parameter(description = "Email à rechercher")
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
        response.setConfirmedAt(user.getConfirmedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setAvatarUrl(user.getAvatarUrl());
        return response;
    }
}