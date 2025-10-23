package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour UserIdentityService.
 * Type: Tests Unitaires
 * Ces tests vérifient la logique de gestion des identités utilisateur
 * (local et OAuth) sans dépendances réelles.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserIdentityService - Tests Unitaires")
class UserIdentityServiceTest {

    @Mock
    private UserLocalIdentityRepository userLocalIdentityRepository;

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @InjectMocks
    private UserIdentityService userIdentityService;

    private User testUser;
    private UserLocalIdentity testLocalIdentity;
    private UserOAuthIdentity testOAuthIdentity;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        
        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");

        testLocalIdentity = new UserLocalIdentity();
        testLocalIdentity.setId(UUID.randomUUID());
        testLocalIdentity.setUser(testUser);
        testLocalIdentity.setEmail("test@example.com");
        testLocalIdentity.setPasswordHash("hashedpassword");

        testOAuthIdentity = new UserOAuthIdentity();
        testOAuthIdentity.setId(UUID.randomUUID());
        testOAuthIdentity.setUser(testUser);
        testOAuthIdentity.setProvider("google");
        testOAuthIdentity.setProviderUserId("google123");
    }

    @Test
    @DisplayName("Doit détecter le type de création LOCAL")
    void shouldDetectLocalCreationType() {
        // Given
        testLocalIdentity.setPasswordHash("$2a$10$hashedpassword");
        when(userLocalIdentityRepository.findByUserId(userId))
            .thenReturn(Optional.of(testLocalIdentity));
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.Collections.emptyList());

        // When
        UserIdentityService.UserCreationType result = 
            userIdentityService.getUserCreationType(userId);

        // Then
        assertEquals(UserIdentityService.UserCreationType.LOCAL, result);
    }

    @Test
    @DisplayName("Doit détecter le type de création OAUTH")
    void shouldDetectOAuthCreationType() {
        // Given
        when(userLocalIdentityRepository.findByUserId(userId))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.List.of(testOAuthIdentity));

        // When
        UserIdentityService.UserCreationType result = 
            userIdentityService.getUserCreationType(userId);

        // Then
        assertEquals(UserIdentityService.UserCreationType.OAUTH, result);
    }

    @Test
    @DisplayName("Doit détecter le type de création BOTH")
    void shouldDetectBothCreationType() {
        // Given
        testLocalIdentity.setPasswordHash("$2a$10$hashedpassword");
        when(userLocalIdentityRepository.findByUserId(userId))
            .thenReturn(Optional.of(testLocalIdentity));
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.List.of(testOAuthIdentity));

        // When
        UserIdentityService.UserCreationType result = 
            userIdentityService.getUserCreationType(userId);

        // Then
        assertEquals(UserIdentityService.UserCreationType.BOTH, result);
    }

    @Test
    @DisplayName("Doit détecter le type de création UNKNOWN")
    void shouldDetectUnknownCreationType() {
        // Given
        when(userLocalIdentityRepository.findByUserId(userId))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.Collections.emptyList());

        // When
        UserIdentityService.UserCreationType result = 
            userIdentityService.getUserCreationType(userId);

        // Then
        assertEquals(UserIdentityService.UserCreationType.UNKNOWN, result);
    }

    @Test
    @DisplayName("Doit permettre la déconnexion d'un service OAuth quand ce n'est pas le fournisseur principal")
    void shouldAllowDisconnectWhenNotPrimaryProvider() {
        // Given
        UserOAuthIdentity githubIdentity = new UserOAuthIdentity();
        githubIdentity.setProvider("github");
        githubIdentity.setCreatedAt(java.time.LocalDateTime.now().minusDays(1)); // Plus ancien
        
        testOAuthIdentity.setCreatedAt(java.time.LocalDateTime.now()); // Plus récent
        
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.List.of(githubIdentity, testOAuthIdentity));

        // When
        boolean result = userIdentityService.canDisconnectService(userId, "google");

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Ne doit pas permettre la déconnexion du fournisseur OAuth principal")
    void shouldNotAllowDisconnectOfPrimaryOAuthProvider() {
        // Given
        testOAuthIdentity.setCreatedAt(java.time.LocalDateTime.now());
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.List.of(testOAuthIdentity));

        // When
        boolean result = userIdentityService.canDisconnectService(userId, "google");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit déconnecter un service OAuth")
    void shouldDisconnectService() {
        // Given
        UserOAuthIdentity githubIdentity = new UserOAuthIdentity();
        githubIdentity.setProvider("github");
        githubIdentity.setCreatedAt(java.time.LocalDateTime.now().minusDays(1)); // Plus ancien
        
        testOAuthIdentity.setCreatedAt(java.time.LocalDateTime.now()); // Plus récent
        
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.List.of(githubIdentity, testOAuthIdentity));
        doNothing().when(userOAuthIdentityRepository)
            .deleteByUserIdAndProvider(userId, "google");

        // When & Then
        assertDoesNotThrow(() -> 
            userIdentityService.disconnectService(userId, "google")
        );
        
        verify(userOAuthIdentityRepository, times(1))
            .deleteByUserIdAndProvider(userId, "google");
    }

    @Test
    @DisplayName("Doit lever une exception lors de la déconnexion du fournisseur principal")
    void shouldThrowExceptionWhenDisconnectingPrimaryProvider() {
        // Given
        testOAuthIdentity.setCreatedAt(java.time.LocalDateTime.now());
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.List.of(testOAuthIdentity));

        // When & Then
        assertThrows(IllegalStateException.class, () ->
            userIdentityService.disconnectService(userId, "google")
        );
        
        verify(userOAuthIdentityRepository, never())
            .deleteByUserIdAndProvider(any(), any());
    }

    @Test
    @DisplayName("Doit vérifier si l'utilisateur a une identité locale")
    void shouldCheckIfUserHasLocalIdentity() {
        // Given
        testLocalIdentity.setPasswordHash("$2a$10$hashedpassword");
        when(userLocalIdentityRepository.findByUserId(userId))
            .thenReturn(Optional.of(testLocalIdentity));

        // When
        boolean result = userIdentityService.hasLocalIdentity(userId);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit vérifier si l'utilisateur n'a pas d'identité locale")
    void shouldCheckIfUserDoesNotHaveLocalIdentity() {
        // Given
        when(userLocalIdentityRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        // When
        boolean result = userIdentityService.hasLocalIdentity(userId);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit obtenir toutes les identités OAuth de l'utilisateur")
    void shouldGetAllOAuthIdentities() {
        // Given
        UserOAuthIdentity githubIdentity = new UserOAuthIdentity();
        githubIdentity.setProvider("github");
        
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.List.of(testOAuthIdentity, githubIdentity));

        // When
        var identities = userIdentityService.getUserOAuthIdentities(userId);

        // Then
        assertNotNull(identities);
        assertEquals(2, identities.size());
        assertTrue(identities.stream().anyMatch(i -> i.getProvider().equals("google")));
        assertTrue(identities.stream().anyMatch(i -> i.getProvider().equals("github")));
    }

    @Test
    @DisplayName("Doit vérifier si l'utilisateur a un fournisseur OAuth spécifique")
    void shouldCheckIfUserHasSpecificOAuthProvider() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(testOAuthIdentity));

        // When
        boolean result = userIdentityService.hasOAuthIdentity(userId, "google");

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit vérifier si l'utilisateur n'a pas un fournisseur OAuth spécifique")
    void shouldCheckIfUserDoesNotHaveSpecificOAuthProvider() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "github"))
            .thenReturn(Optional.empty());

        // When
        boolean result = userIdentityService.hasOAuthIdentity(userId, "github");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit obtenir le fournisseur OAuth principal")
    void shouldGetPrimaryOAuthProvider() {
        // Given
        UserOAuthIdentity githubIdentity = new UserOAuthIdentity();
        githubIdentity.setProvider("github");
        githubIdentity.setCreatedAt(java.time.LocalDateTime.now().minusDays(1)); // Plus ancien
        
        testOAuthIdentity.setCreatedAt(java.time.LocalDateTime.now()); // Plus récent
        
        when(userOAuthIdentityRepository.findByUserId(userId))
            .thenReturn(java.util.List.of(testOAuthIdentity, githubIdentity));

        // When
        Optional<String> primaryProvider = userIdentityService.getPrimaryOAuthProvider(userId);

        // Then
        assertTrue(primaryProvider.isPresent());
        assertEquals("github", primaryProvider.get());
    }

    @Test
    @DisplayName("Doit vérifier si l'utilisateur a besoin de se connecter à un service")
    void shouldCheckIfUserNeedsServiceConnection() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "github"))
            .thenReturn(Optional.empty());

        // When
        boolean result = userIdentityService.needsServiceConnection(userId, "github");

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit obtenir une identité OAuth spécifique")
    void shouldGetSpecificOAuthIdentity() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(testOAuthIdentity));

        // When
        Optional<UserOAuthIdentity> identity = userIdentityService.getOAuthIdentity(userId, "google");

        // Then
        assertTrue(identity.isPresent());
        assertEquals("google", identity.get().getProvider());
    }
}

