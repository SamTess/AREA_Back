package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleActionService
 * Tests Google API interactions for Gmail, Calendar, Drive, and Sheets
 */
@ExtendWith(MockitoExtension.class)
class GoogleActionServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private ServiceAccountService serviceAccountService;

    private SimpleMeterRegistry meterRegistry;
    private GoogleActionService googleActionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        googleActionService = new GoogleActionService(
            userOAuthIdentityRepository,
            userRepository,
            tokenEncryptionService,
            serviceAccountService,
            meterRegistry
        );

        // Manually initialize metrics
        try {
            var initMethod = GoogleActionService.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(googleActionService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(googleActionService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("google_actions_executed_total").counter());
        assertNotNull(meterRegistry.find("google_actions_failed_total").counter());
    }

    @Test
    void testExecuteGoogleActionWithoutToken() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("gmail_send_email", inputPayload, actionParams, userId);
        });

        assertTrue(exception.getMessage().contains("No Google token found"));
    }

    @Test
    void testExecuteGoogleActionWithUnknownAction() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("unknown_action", inputPayload, actionParams, userId);
        });

        assertTrue(exception.getMessage().contains("Unknown Google action"));
    }

    @Test
    void testExecuteGoogleActionIncrementsMetrics() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        double initialExecuted = meterRegistry.counter("google_actions_executed_total").count();
        double initialFailed = meterRegistry.counter("google_actions_failed_total").count();

        // When
        try {
            googleActionService.executeGoogleAction("gmail_send_email", inputPayload, actionParams, userId);
        } catch (Exception e) {
            // Expected to fail
        }

        // Then
        double finalExecuted = meterRegistry.counter("google_actions_executed_total").count();
        double finalFailed = meterRegistry.counter("google_actions_failed_total").count();

        assertEquals(initialExecuted + 1, finalExecuted);
        assertEquals(initialFailed + 1, finalFailed);
    }

    @Test
    void testCheckGoogleEventsWithoutToken() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.empty());

        // When
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "gmail_new_email", actionParams, userId, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void testCheckGoogleEventsWithUnknownAction() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "unknown_event", actionParams, userId, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void testCheckGoogleEventsHandlesException() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token"))
            .thenThrow(new RuntimeException("Decryption failed"));

        // When
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "gmail_new_email", actionParams, userId, lastCheck
        );

        // Then - should return empty list instead of throwing
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void testGmailSendEmailActionWithMissingRequiredParams() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        // Missing required params: to, subject, body

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("gmail_send_email", inputPayload, actionParams, userId);
        });
    }

    @Test
    void testCalendarCreateEventActionKey() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When & Then - should fail due to missing params or API call
        assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("calendar_create_event", inputPayload, actionParams, userId);
        });
    }

    @Test
    void testDriveCreateFolderActionKey() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("drive_create_folder", inputPayload, actionParams, userId);
        });
    }

    @Test
    void testSheetsAddRowActionKey() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleActionService.executeGoogleAction("sheets_add_row", inputPayload, actionParams, userId);
        });
    }

    @Test
    void testCheckNewGmailMessagesEventKey() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When - should fail or return empty list due to API call failure
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "gmail_new_email", actionParams, userId, lastCheck
        );

        // Then - returns empty list on API failure
        assertNotNull(events);
    }

    @Test
    void testCheckNewCalendarEventsEventKey() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "calendar_new_event", actionParams, userId, lastCheck
        );

        // Then
        assertNotNull(events);
    }

    @Test
    void testCheckNewDriveFilesEventKey() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        User user = new User();
        user.setId(userId);

        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted-token");
        oauthIdentity.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn("valid-token");

        // When
        List<Map<String, Object>> events = googleActionService.checkGoogleEvents(
            "drive_new_file", actionParams, userId, lastCheck
        );

        // Then
        assertNotNull(events);
    }
}
