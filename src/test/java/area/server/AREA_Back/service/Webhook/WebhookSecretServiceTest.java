package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookSecretServiceTest {

    @Mock
    private ServiceAccountRepository serviceAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @InjectMocks
    private WebhookSecretService webhookSecretService;

    private UUID userId;
    private User user;
    private area.server.AREA_Back.entity.Service service;
    private ServiceAccount serviceAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);

        service = new area.server.AREA_Back.entity.Service();
        service.setKey("github");

        serviceAccount = new ServiceAccount();
        serviceAccount.setUser(user);
        serviceAccount.setService(service);

        // Set default secrets via reflection
        ReflectionTestUtils.setField(webhookSecretService, "githubWebhookSecret", "github_secret");
        ReflectionTestUtils.setField(webhookSecretService, "slackWebhookSecret", "slack_secret");
        ReflectionTestUtils.setField(webhookSecretService, "discordPublicKey", "discord_secret");
    }

    @Test
    void testGetServiceSecret_GitHub() {
        String secret = webhookSecretService.getServiceSecret("github");

        assertEquals("github_secret", secret);
    }

    @Test
    void testGetServiceSecret_Slack() {
        String secret = webhookSecretService.getServiceSecret("slack");

        assertEquals("slack_secret", secret);
    }

    @Test
    void testGetServiceSecret_Discord() {
        String secret = webhookSecretService.getServiceSecret("discord");

        assertEquals("discord_secret", secret);
    }

    @Test
    void testGetServiceSecret_Unknown() {
        String secret = webhookSecretService.getServiceSecret("unknown");

        assertNull(secret);
    }

    @Test
    void testGetServiceSecret_Null() {
        String secret = webhookSecretService.getServiceSecret(null);

        assertNull(secret);
    }

    @Test
    void testGetServiceSecret_CaseInsensitive() {
        String secret1 = webhookSecretService.getServiceSecret("GITHUB");
        String secret2 = webhookSecretService.getServiceSecret("GitHub");
        String secret3 = webhookSecretService.getServiceSecret("github");

        assertEquals("github_secret", secret1);
        assertEquals("github_secret", secret2);
        assertEquals("github_secret", secret3);
    }

    @Test
    void testGetServiceSecret_Cached() {
        // First call - should cache
        String secret1 = webhookSecretService.getServiceSecret("github");
        
        // Second call - should use cache
        String secret2 = webhookSecretService.getServiceSecret("github");

        assertEquals(secret1, secret2);
    }

    @Test
    void testGetUserServiceSecret_WithUserSecret() {
        serviceAccount.setWebhookSecretEnc("encrypted_secret");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceRepository.findByKey("github")).thenReturn(Optional.of(service));
        when(serviceAccountRepository.findByUserAndService(user, service))
            .thenReturn(Optional.of(serviceAccount));
        when(tokenEncryptionService.decryptToken("encrypted_secret"))
            .thenReturn("decrypted_user_secret");

        String secret = webhookSecretService.getUserServiceSecret("github", userId);

        assertEquals("decrypted_user_secret", secret);
        verify(tokenEncryptionService).decryptToken("encrypted_secret");
    }

    @Test
    void testGetUserServiceSecret_NoUserSecret_FallbackToDefault() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceRepository.findByKey("github")).thenReturn(Optional.of(service));
        when(serviceAccountRepository.findByUserAndService(user, service))
            .thenReturn(Optional.empty());

        String secret = webhookSecretService.getUserServiceSecret("github", userId);

        assertEquals("github_secret", secret);
    }

    @Test
    void testGetUserServiceSecret_NullUserId() {
        String secret = webhookSecretService.getUserServiceSecret("github", null);

        assertNull(secret);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void testGetUserServiceSecret_NullService() {
        String secret = webhookSecretService.getUserServiceSecret(null, userId);

        assertNull(secret);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void testGetUserServiceSecret_UserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        String secret = webhookSecretService.getUserServiceSecret("github", userId);

        assertEquals("github_secret", secret);
    }

    @Test
    void testGetUserServiceSecret_ServiceNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceRepository.findByKey("github")).thenReturn(Optional.empty());

        String secret = webhookSecretService.getUserServiceSecret("github", userId);

        assertEquals("github_secret", secret);
    }

    @Test
    void testGetUserServiceSecret_DecryptionFailure() {
        serviceAccount.setWebhookSecretEnc("encrypted_secret");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceRepository.findByKey("github")).thenReturn(Optional.of(service));
        when(serviceAccountRepository.findByUserAndService(user, service))
            .thenReturn(Optional.of(serviceAccount));
        when(tokenEncryptionService.decryptToken("encrypted_secret"))
            .thenThrow(new RuntimeException("Decryption failed"));

        String secret = webhookSecretService.getUserServiceSecret("github", userId);

        assertNull(secret);
    }

    @Test
    void testGetUserServiceSecret_NullEncryptedSecret() {
        serviceAccount.setWebhookSecretEnc(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceRepository.findByKey("github")).thenReturn(Optional.of(service));
        when(serviceAccountRepository.findByUserAndService(user, service))
            .thenReturn(Optional.of(serviceAccount));

        String secret = webhookSecretService.getUserServiceSecret("github", userId);

        assertEquals("github_secret", secret);
        verify(tokenEncryptionService, never()).decryptToken(anyString());
    }

    @Test
    void testGetUserServiceSecret_ExceptionHandling() {
        when(userRepository.findById(userId))
            .thenThrow(new RuntimeException("Database error"));

        String secret = webhookSecretService.getUserServiceSecret("github", userId);

        assertEquals("github_secret", secret);
    }

    @Test
    void testHasServiceSecret_True() {
        boolean result = webhookSecretService.hasServiceSecret("github");

        assertTrue(result);
    }

    @Test
    void testHasServiceSecret_False() {
        boolean result = webhookSecretService.hasServiceSecret("unknown");

        assertFalse(result);
    }

    @Test
    void testHasServiceSecret_Null() {
        boolean result = webhookSecretService.hasServiceSecret(null);

        assertFalse(result);
    }

    @Test
    void testHasUserServiceSecret_True() {
        serviceAccount.setWebhookSecretEnc("encrypted_secret");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceRepository.findByKey("github")).thenReturn(Optional.of(service));
        when(serviceAccountRepository.findByUserAndService(user, service))
            .thenReturn(Optional.of(serviceAccount));
        when(tokenEncryptionService.decryptToken("encrypted_secret"))
            .thenReturn("decrypted_secret");

        boolean result = webhookSecretService.hasUserServiceSecret("github", userId);

        assertTrue(result);
    }

    @Test
    void testHasUserServiceSecret_False() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        boolean result = webhookSecretService.hasUserServiceSecret("unknown", userId);

        assertFalse(result);
    }

    @Test
    void testHasUserServiceSecret_NullService() {
        boolean result = webhookSecretService.hasUserServiceSecret(null, userId);

        assertFalse(result);
    }

    @Test
    void testHasUserServiceSecret_NullUserId() {
        boolean result = webhookSecretService.hasUserServiceSecret("github", null);

        assertFalse(result);
    }

    @Test
    void testClearCache() {
        // Populate cache
        webhookSecretService.getServiceSecret("github");
        webhookSecretService.getServiceSecret("slack");

        assertDoesNotThrow(() -> webhookSecretService.clearCache());

        // After clear, should still work
        String secret = webhookSecretService.getServiceSecret("github");
        assertEquals("github_secret", secret);
    }

    @Test
    void testGetServiceSecret_AllServices() {
        String githubSecret = webhookSecretService.getServiceSecret("github");
        String slackSecret = webhookSecretService.getServiceSecret("slack");
        String discordSecret = webhookSecretService.getServiceSecret("discord");

        assertEquals("github_secret", githubSecret);
        assertEquals("slack_secret", slackSecret);
        assertEquals("discord_secret", discordSecret);
    }

    @Test
    void testGetUserServiceSecret_MultipleServices() {
        area.server.AREA_Back.entity.Service slackService = new area.server.AREA_Back.entity.Service();
        slackService.setKey("slack");

        ServiceAccount slackAccount = new ServiceAccount();
        slackAccount.setUser(user);
        slackAccount.setService(slackService);
        slackAccount.setWebhookSecretEnc("slack_encrypted");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        when(serviceRepository.findByKey("github")).thenReturn(Optional.of(service));
        when(serviceRepository.findByKey("slack")).thenReturn(Optional.of(slackService));
        
        when(serviceAccountRepository.findByUserAndService(user, service))
            .thenReturn(Optional.empty());
        when(serviceAccountRepository.findByUserAndService(user, slackService))
            .thenReturn(Optional.of(slackAccount));
        
        when(tokenEncryptionService.decryptToken("slack_encrypted"))
            .thenReturn("slack_decrypted");

        String githubSecret = webhookSecretService.getUserServiceSecret("github", userId);
        String slackSecret = webhookSecretService.getUserServiceSecret("slack", userId);

        assertEquals("github_secret", githubSecret);
        assertEquals("slack_decrypted", slackSecret);
    }

    @Test
    void testGetServiceSecret_NoSecretsConfigured() {
        ReflectionTestUtils.setField(webhookSecretService, "githubWebhookSecret", null);
        webhookSecretService.clearCache();

        String secret = webhookSecretService.getServiceSecret("github");

        assertNull(secret);
    }
}
