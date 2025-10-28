package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour ServiceAccountService.
 * Type: Tests Unitaires
 * Ces tests vérifient la logique de gestion des comptes de service
 * sans dépendances réelles.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceAccountService - Tests Unitaires")
class ServiceAccountServiceTest {

    @Mock
    private ServiceAccountRepository serviceAccountRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @InjectMocks
    private ServiceAccountService serviceAccountService;

    private User testUser;
    private Service testService;
    private ServiceAccount testServiceAccount;
    private UUID userId;
    private String serviceKey;
    private String accessToken;
    private String refreshToken;
    private String encryptedAccessToken;
    private String encryptedRefreshToken;
    private LocalDateTime expiresAt;
    private Map<String, Object> scopes;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        serviceKey = "github";
        accessToken = "github_token_123";
        refreshToken = "github_refresh_456";
        encryptedAccessToken = "encrypted_access_token";
        encryptedRefreshToken = "encrypted_refresh_token";
        expiresAt = LocalDateTime.now().plusHours(1);
        scopes = new HashMap<>();
        scopes.put("repo", true);
        scopes.put("user", true);

        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");

        testService = new Service();
        testService.setId(UUID.randomUUID());
        testService.setKey(serviceKey);
        testService.setName("GitHub");
        testService.setAuth(Service.AuthType.OAUTH2);

        testServiceAccount = new ServiceAccount();
        testServiceAccount.setId(UUID.randomUUID());
        testServiceAccount.setUser(testUser);
        testServiceAccount.setService(testService);
        testServiceAccount.setAccessTokenEnc(encryptedAccessToken);
        testServiceAccount.setRefreshTokenEnc(encryptedRefreshToken);
        testServiceAccount.setExpiresAt(expiresAt);
        testServiceAccount.setScopes(scopes);
        testServiceAccount.setTokenVersion(1);
        testServiceAccount.setLastRefreshAt(LocalDateTime.now());
    }

    // ==================== Tests pour createOrUpdateServiceAccount ====================

    @Test
    @DisplayName("Doit créer un nouveau service account quand il n'existe pas")
    void shouldCreateNewServiceAccountWhenNotExists() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.empty());
        when(tokenEncryptionService.encryptToken(accessToken)).thenReturn(encryptedAccessToken);
        when(tokenEncryptionService.encryptToken(refreshToken)).thenReturn(encryptedRefreshToken);
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        ServiceAccount result = serviceAccountService.createOrUpdateServiceAccount(
            userId, serviceKey, accessToken, refreshToken, expiresAt, scopes);

        // Then
        assertNotNull(result);
        assertEquals(testUser, result.getUser());
        assertEquals(testService, result.getService());
        assertEquals(encryptedAccessToken, result.getAccessTokenEnc());
        assertEquals(encryptedRefreshToken, result.getRefreshTokenEnc());
        assertEquals(expiresAt, result.getExpiresAt());
        assertEquals(scopes, result.getScopes());
        // Pour un nouveau compte, l'entité ServiceAccount initialise tokenVersion à 1,
        // et le code l'incrémente, donc on obtient 2
        assertEquals(2, result.getTokenVersion());
        assertNull(result.getRevokedAt());
        assertNotNull(result.getLastRefreshAt());

        verify(userRepository).findById(userId);
        verify(serviceRepository).findByKey(serviceKey);
        verify(serviceAccountRepository).findByUserAndService(testUser, testService);
        verify(tokenEncryptionService).encryptToken(accessToken);
        verify(tokenEncryptionService).encryptToken(refreshToken);
        verify(serviceAccountRepository).save(any(ServiceAccount.class));
    }

    @Test
    @DisplayName("Doit mettre à jour un service account existant")
    void shouldUpdateExistingServiceAccount() {
        // Given
        testServiceAccount.setTokenVersion(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(tokenEncryptionService.encryptToken(accessToken)).thenReturn(encryptedAccessToken);
        when(tokenEncryptionService.encryptToken(refreshToken)).thenReturn(encryptedRefreshToken);
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        ServiceAccount result = serviceAccountService.createOrUpdateServiceAccount(
            userId, serviceKey, accessToken, refreshToken, expiresAt, scopes);

        // Then
        assertNotNull(result);
        assertEquals(3, result.getTokenVersion()); // Version incrémentée de 2 à 3
        assertEquals(encryptedAccessToken, result.getAccessTokenEnc());
        assertEquals(encryptedRefreshToken, result.getRefreshTokenEnc());
        assertNull(result.getRevokedAt());

        verify(serviceAccountRepository).save(any(ServiceAccount.class));
    }

    @Test
    @DisplayName("Doit ignorer les tokens vides ou null lors de la création")
    void shouldIgnoreEmptyOrNullTokens() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.empty());
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When - avec token null
        ServiceAccount result1 = serviceAccountService.createOrUpdateServiceAccount(
            userId, serviceKey, null, null, expiresAt, scopes);

        // Then
        assertNotNull(result1);
        assertNull(result1.getAccessTokenEnc());
        assertNull(result1.getRefreshTokenEnc());

        // When - avec token vide
        ServiceAccount result2 = serviceAccountService.createOrUpdateServiceAccount(
            userId, serviceKey, "", "  ", expiresAt, scopes);

        // Then
        assertNotNull(result2);
        assertNull(result2.getAccessTokenEnc());
        assertNull(result2.getRefreshTokenEnc());

        verify(tokenEncryptionService, never()).encryptToken(anyString());
    }

    @Test
    @DisplayName("Doit initialiser la version à 1 si null")
    void shouldInitializeTokenVersionToOneWhenNull() {
        // Given
        testServiceAccount.setTokenVersion(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        ServiceAccount result = serviceAccountService.createOrUpdateServiceAccount(
            userId, serviceKey, accessToken, refreshToken, expiresAt, scopes);

        // Then
        assertEquals(1, result.getTokenVersion());
    }

    @Test
    @DisplayName("Doit lever une exception si l'utilisateur n'existe pas")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> serviceAccountService.createOrUpdateServiceAccount(
                userId, serviceKey, accessToken, refreshToken, expiresAt, scopes)
        );

        assertEquals("User not found: " + userId, exception.getMessage());
        verify(userRepository).findById(userId);
        verify(serviceRepository, never()).findByKey(anyString());
        verify(serviceAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Doit lever une exception si le service n'existe pas")
    void shouldThrowExceptionWhenServiceNotFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> serviceAccountService.createOrUpdateServiceAccount(
                userId, serviceKey, accessToken, refreshToken, expiresAt, scopes)
        );

        assertEquals("Service not found: " + serviceKey, exception.getMessage());
        verify(userRepository).findById(userId);
        verify(serviceRepository).findByKey(serviceKey);
        verify(serviceAccountRepository, never()).save(any());
    }

    // ==================== Tests pour getServiceAccount ====================

    @Test
    @DisplayName("Doit retourner le service account s'il existe et n'est pas révoqué")
    void shouldReturnServiceAccountWhenExistsAndNotRevoked() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));

        // When
        Optional<ServiceAccount> result = serviceAccountService.getServiceAccount(userId, serviceKey);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testServiceAccount, result.get());
        verify(userRepository).findById(userId);
        verify(serviceRepository).findByKey(serviceKey);
        verify(serviceAccountRepository).findByUserAndService(testUser, testService);
    }

    @Test
    @DisplayName("Doit retourner empty si le service account est révoqué")
    void shouldReturnEmptyWhenServiceAccountIsRevoked() {
        // Given
        testServiceAccount.setRevokedAt(LocalDateTime.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));

        // When
        Optional<ServiceAccount> result = serviceAccountService.getServiceAccount(userId, serviceKey);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Doit retourner empty si le service account n'existe pas")
    void shouldReturnEmptyWhenServiceAccountNotFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.empty());

        // When
        Optional<ServiceAccount> result = serviceAccountService.getServiceAccount(userId, serviceKey);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Doit retourner empty en cas d'erreur (utilisateur non trouvé)")
    void shouldReturnEmptyWhenExceptionOccursUserNotFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        Optional<ServiceAccount> result = serviceAccountService.getServiceAccount(userId, serviceKey);

        // Then
        assertFalse(result.isPresent());
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("Doit retourner empty en cas d'erreur (service non trouvé)")
    void shouldReturnEmptyWhenExceptionOccursServiceNotFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.empty());

        // When
        Optional<ServiceAccount> result = serviceAccountService.getServiceAccount(userId, serviceKey);

        // Then
        assertFalse(result.isPresent());
        verify(serviceRepository).findByKey(serviceKey);
    }

    // ==================== Tests pour getAccessToken ====================

    @Test
    @DisplayName("Doit retourner le token décrypté si disponible")
    void shouldReturnDecryptedAccessToken() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(tokenEncryptionService.decryptToken(encryptedAccessToken)).thenReturn(accessToken);

        // When
        Optional<String> result = serviceAccountService.getAccessToken(userId, serviceKey);

        // Then
        assertTrue(result.isPresent());
        assertEquals(accessToken, result.get());
        verify(tokenEncryptionService).decryptToken(encryptedAccessToken);
    }

    @Test
    @DisplayName("Doit retourner Optional avec null si le token encrypté est null")
    void shouldReturnEmptyWhenAccessTokenEncIsNull() {
        // Given
        testServiceAccount.setAccessTokenEnc(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));

        // When
        Optional<String> result = serviceAccountService.getAccessToken(userId, serviceKey);

        // Then
        // La méthode map() retourne Optional.empty() quand la lambda retourne null
        assertFalse(result.isPresent());
        verify(tokenEncryptionService, never()).decryptToken(anyString());
    }

    @Test
    @DisplayName("Doit retourner Optional vide si le décryptage échoue")
    void shouldReturnNullWhenDecryptionFails() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(tokenEncryptionService.decryptToken(encryptedAccessToken))
            .thenThrow(new RuntimeException("Decryption failed"));

        // When
        Optional<String> result = serviceAccountService.getAccessToken(userId, serviceKey);

        // Then
        // La méthode map() retourne Optional.empty() quand la lambda retourne null
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Doit retourner empty si le service account n'existe pas")
    void shouldReturnEmptyWhenNoServiceAccountForAccessToken() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.empty());

        // When
        Optional<String> result = serviceAccountService.getAccessToken(userId, serviceKey);

        // Then
        assertFalse(result.isPresent());
    }

    // ==================== Tests pour getUserServiceAccounts ====================

    @Test
    @DisplayName("Doit retourner tous les service accounts actifs d'un utilisateur")
    void shouldReturnAllActiveServiceAccountsForUser() {
        // Given
        List<ServiceAccount> serviceAccounts = Arrays.asList(testServiceAccount);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceAccountRepository.findActiveByUser(testUser)).thenReturn(serviceAccounts);

        // When
        List<ServiceAccount> result = serviceAccountService.getUserServiceAccounts(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testServiceAccount, result.get(0));
        verify(userRepository).findById(userId);
        verify(serviceAccountRepository).findActiveByUser(testUser);
    }

    @Test
    @DisplayName("Doit retourner une liste vide si l'utilisateur n'a pas de service accounts")
    void shouldReturnEmptyListWhenUserHasNoServiceAccounts() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceAccountRepository.findActiveByUser(testUser)).thenReturn(Collections.emptyList());

        // When
        List<ServiceAccount> result = serviceAccountService.getUserServiceAccounts(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Doit lever une exception si l'utilisateur n'existe pas")
    void shouldThrowExceptionWhenUserNotFoundForGetUserServiceAccounts() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> serviceAccountService.getUserServiceAccounts(userId)
        );

        assertEquals("User not found: " + userId, exception.getMessage());
        verify(userRepository).findById(userId);
        verify(serviceAccountRepository, never()).findActiveByUser(any());
    }

    // ==================== Tests pour revokeServiceAccount ====================

    @Test
    @DisplayName("Doit révoquer un service account existant")
    void shouldRevokeServiceAccount() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        serviceAccountService.revokeServiceAccount(userId, serviceKey);

        // Then
        assertNotNull(testServiceAccount.getRevokedAt());
        verify(serviceAccountRepository).save(testServiceAccount);
    }

    @Test
    @DisplayName("Ne doit rien faire si le service account n'existe pas")
    void shouldDoNothingWhenRevokingNonExistentServiceAccount() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.empty());

        // When
        serviceAccountService.revokeServiceAccount(userId, serviceKey);

        // Then
        verify(serviceAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ne doit rien faire si le service account est déjà révoqué")
    void shouldDoNothingWhenRevokingAlreadyRevokedServiceAccount() {
        // Given
        testServiceAccount.setRevokedAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));

        // When
        serviceAccountService.revokeServiceAccount(userId, serviceKey);

        // Then
        verify(serviceAccountRepository, never()).save(any());
    }

    // ==================== Tests pour hasValidToken ====================

    @Test
    @DisplayName("Doit retourner true si le token est valide et non expiré")
    void shouldReturnTrueWhenTokenIsValidAndNotExpired() {
        // Given
        testServiceAccount.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));

        // When
        boolean result = serviceAccountService.hasValidToken(userId, serviceKey);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit retourner true si le token n'a pas de date d'expiration")
    void shouldReturnTrueWhenTokenHasNoExpiration() {
        // Given
        testServiceAccount.setExpiresAt(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));

        // When
        boolean result = serviceAccountService.hasValidToken(userId, serviceKey);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit retourner false si le token est expiré")
    void shouldReturnFalseWhenTokenIsExpired() {
        // Given
        testServiceAccount.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));

        // When
        boolean result = serviceAccountService.hasValidToken(userId, serviceKey);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit retourner false si le token est null")
    void shouldReturnFalseWhenAccessTokenIsNull() {
        // Given
        testServiceAccount.setAccessTokenEnc(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));

        // When
        boolean result = serviceAccountService.hasValidToken(userId, serviceKey);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit retourner false si le service account n'existe pas")
    void shouldReturnFalseWhenServiceAccountDoesNotExist() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.empty());

        // When
        boolean result = serviceAccountService.hasValidToken(userId, serviceKey);

        // Then
        assertFalse(result);
    }

    // ==================== Tests pour storeTokenMetadata ====================

    @Test
    @DisplayName("Doit stocker le remoteAccountId")
    void shouldStoreRemoteAccountId() {
        // Given
        String remoteAccountId = "remote123";
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        serviceAccountService.storeTokenMetadata(userId, serviceKey, remoteAccountId, null, null);

        // Then
        assertEquals(remoteAccountId, testServiceAccount.getRemoteAccountId());
        verify(serviceAccountRepository).save(testServiceAccount);
    }

    @Test
    @DisplayName("Doit stocker le webhookSecret encrypté")
    void shouldStoreEncryptedWebhookSecret() {
        // Given
        String webhookSecret = "webhook_secret_123";
        String encryptedWebhookSecret = "encrypted_webhook_secret";
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(tokenEncryptionService.encryptToken(webhookSecret)).thenReturn(encryptedWebhookSecret);
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        serviceAccountService.storeTokenMetadata(userId, serviceKey, null, webhookSecret, null);

        // Then
        assertEquals(encryptedWebhookSecret, testServiceAccount.getWebhookSecretEnc());
        verify(tokenEncryptionService).encryptToken(webhookSecret);
        verify(serviceAccountRepository).save(testServiceAccount);
    }

    @Test
    @DisplayName("Doit fusionner les scopes additionnels avec les scopes existants")
    void shouldMergeAdditionalScopesWithExistingScopes() {
        // Given
        Map<String, Object> additionalScopes = new HashMap<>();
        additionalScopes.put("newScope", "newValue");
        additionalScopes.put("repo", false); // Override existing

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        serviceAccountService.storeTokenMetadata(userId, serviceKey, null, null, additionalScopes);

        // Then
        Map<String, Object> resultScopes = testServiceAccount.getScopes();
        assertEquals(3, resultScopes.size());
        assertEquals("newValue", resultScopes.get("newScope"));
        assertEquals(false, resultScopes.get("repo")); // Updated value
        assertEquals(true, resultScopes.get("user")); // Original value preserved
        verify(serviceAccountRepository).save(testServiceAccount);
    }

    @Test
    @DisplayName("Doit créer les scopes si ils sont null")
    void shouldCreateScopesWhenNull() {
        // Given
        testServiceAccount.setScopes(null);
        Map<String, Object> additionalScopes = new HashMap<>();
        additionalScopes.put("newScope", "newValue");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        serviceAccountService.storeTokenMetadata(userId, serviceKey, null, null, additionalScopes);

        // Then
        assertNotNull(testServiceAccount.getScopes());
        assertEquals(additionalScopes, testServiceAccount.getScopes());
        verify(serviceAccountRepository).save(testServiceAccount);
    }

    @Test
    @DisplayName("Doit stocker toutes les métadonnées en une fois")
    void shouldStoreAllMetadataAtOnce() {
        // Given
        String remoteAccountId = "remote123";
        String webhookSecret = "webhook_secret_123";
        String encryptedWebhookSecret = "encrypted_webhook_secret";
        Map<String, Object> additionalScopes = new HashMap<>();
        additionalScopes.put("newScope", "newValue");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(tokenEncryptionService.encryptToken(webhookSecret)).thenReturn(encryptedWebhookSecret);
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        serviceAccountService.storeTokenMetadata(userId, serviceKey, remoteAccountId, 
                                                webhookSecret, additionalScopes);

        // Then
        assertEquals(remoteAccountId, testServiceAccount.getRemoteAccountId());
        assertEquals(encryptedWebhookSecret, testServiceAccount.getWebhookSecretEnc());
        assertTrue(testServiceAccount.getScopes().containsKey("newScope"));
        verify(serviceAccountRepository).save(testServiceAccount);
    }

    @Test
    @DisplayName("Ne doit rien faire si le service account n'existe pas")
    void shouldDoNothingWhenServiceAccountNotFoundForMetadata() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.empty());

        // When
        serviceAccountService.storeTokenMetadata(userId, serviceKey, "remoteId", null, null);

        // Then
        verify(serviceAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ne doit pas modifier les données si tous les paramètres sont null")
    void shouldNotModifyDataWhenAllParametersAreNull() {
        // Given
        ServiceAccount originalAccount = new ServiceAccount();
        originalAccount.setId(testServiceAccount.getId());
        originalAccount.setUser(testServiceAccount.getUser());
        originalAccount.setService(testServiceAccount.getService());
        originalAccount.setRemoteAccountId(testServiceAccount.getRemoteAccountId());
        originalAccount.setWebhookSecretEnc(testServiceAccount.getWebhookSecretEnc());
        originalAccount.setScopes(new HashMap<>(testServiceAccount.getScopes()));

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(serviceRepository.findByKey(serviceKey)).thenReturn(Optional.of(testService));
        when(serviceAccountRepository.findByUserAndService(testUser, testService))
            .thenReturn(Optional.of(testServiceAccount));
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(i -> i.getArgument(0));

        // When
        serviceAccountService.storeTokenMetadata(userId, serviceKey, null, null, null);

        // Then
        assertEquals(originalAccount.getRemoteAccountId(), testServiceAccount.getRemoteAccountId());
        assertEquals(originalAccount.getWebhookSecretEnc(), testServiceAccount.getWebhookSecretEnc());
        assertEquals(originalAccount.getScopes(), testServiceAccount.getScopes());
        verify(serviceAccountRepository).save(testServiceAccount);
    }
}
