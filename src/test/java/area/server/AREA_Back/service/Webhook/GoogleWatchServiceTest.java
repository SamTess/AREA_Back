package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleWatchServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Counter watchStartCounter;

    @Mock
    private Counter watchStopCounter;

    @Mock
    private Counter watchFailures;

    private GoogleWatchService googleWatchService;
    private ObjectMapper objectMapper;
    private MockedStatic<Counter> counterMockedStatic;

    private UUID userId;
    private User user;
    private UserOAuthIdentity oauthIdentity;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("test@gmail.com");

        oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("google");
        oauthIdentity.setAccessTokenEnc("encrypted_token");

        // Initialize real ObjectMapper
        objectMapper = new ObjectMapper();

        // Create service with real ObjectMapper
        googleWatchService = new GoogleWatchService(
            meterRegistry,
            userOAuthIdentityRepository,
            tokenEncryptionService,
            restTemplate,
            objectMapper
        );

        // Setup counter builder chain
        Counter.Builder startBuilder = mock(Counter.Builder.class);
        Counter.Builder stopBuilder = mock(Counter.Builder.class);
        Counter.Builder failureBuilder = mock(Counter.Builder.class);

        // Set up static mocking for Counter
        counterMockedStatic = mockStatic(Counter.class);
        counterMockedStatic.when(() -> Counter.builder("google.watch.start")).thenReturn(startBuilder);
        counterMockedStatic.when(() -> Counter.builder("google.watch.stop")).thenReturn(stopBuilder);
        counterMockedStatic.when(() -> Counter.builder("google.watch.failures")).thenReturn(failureBuilder);

        when(startBuilder.description(anyString())).thenReturn(startBuilder);
        when(stopBuilder.description(anyString())).thenReturn(stopBuilder);
        when(failureBuilder.description(anyString())).thenReturn(failureBuilder);

        when(startBuilder.register(meterRegistry)).thenReturn(watchStartCounter);
        when(stopBuilder.register(meterRegistry)).thenReturn(watchStopCounter);
        when(failureBuilder.register(meterRegistry)).thenReturn(watchFailures);

        // Inject test values
        ReflectionTestUtils.setField(googleWatchService, "gmailTopicName",
            "projects/test-project/topics/gmail-watch");
        ReflectionTestUtils.setField(googleWatchService, "webhookBaseUrl",
            "http://localhost:8080");
        ReflectionTestUtils.setField(googleWatchService, "watchStartCounter", watchStartCounter);
        ReflectionTestUtils.setField(googleWatchService, "watchStopCounter", watchStopCounter);
        ReflectionTestUtils.setField(googleWatchService, "watchFailures", watchFailures);

        // Initialize metrics
        googleWatchService.initMetrics();
    }

    @AfterEach
    void tearDown() {
        if (counterMockedStatic != null) {
            counterMockedStatic.close();
        }
    }

    @Test
    void testInitMetrics() {
        // Given - already set up in @BeforeEach with MockedStatic

        // When - already called in @BeforeEach
        // googleWatchService.initMetrics();

        // Then - verify counters were registered (already done in setUp)
        assertNotNull(ReflectionTestUtils.getField(googleWatchService, "watchStartCounter"));
        assertNotNull(ReflectionTestUtils.getField(googleWatchService, "watchStopCounter"));
        assertNotNull(ReflectionTestUtils.getField(googleWatchService, "watchFailures"));
    }

    @Test
    void testStartGmailWatch_Success() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        String responseBody = "{\"historyId\":\"12345\",\"expiration\":\"1234567890\"}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        // When
        Map<String, Object> result = googleWatchService.startGmailWatch(userId);

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("gmail", result.get("service"));
        assertEquals(userId, result.get("userId"));
        assertEquals("12345", result.get("historyId"));
        assertEquals("1234567890", result.get("expiration"));

        verify(watchStartCounter).increment();
        verify(userOAuthIdentityRepository).findByUserIdAndProvider(userId, "google");
        verify(tokenEncryptionService).decryptToken("encrypted_token");
        verify(restTemplate).postForEntity(
            eq("https://gmail.googleapis.com/gmail/v1/users/me/watch"),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void testStartGmailWatch_WithCustomLabels() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        String responseBody = "{\"historyId\":\"12345\",\"expiration\":\"1234567890\"}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        // When
        Map<String, Object> result = googleWatchService.startGmailWatch(userId, "SENT", "DRAFTS");

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("gmail", result.get("service"));
        assertEquals(userId, result.get("userId"));
        assertEquals("12345", result.get("historyId"));

        verify(watchStartCounter).increment();
        verify(restTemplate).postForEntity(
            eq("https://gmail.googleapis.com/gmail/v1/users/me/watch"),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void testStartGmailWatch_NoOAuthIdentity() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            googleWatchService.startGmailWatch(userId)
        );

        assertTrue(exception.getMessage().contains("No Google OAuth identity found for user"));
        verify(watchStartCounter).increment();
        verify(watchFailures).increment();
        verify(userOAuthIdentityRepository).findByUserIdAndProvider(userId, "google");
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void testStartGmailWatch_ApiError() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"error\":\"Invalid request\"}",
            HttpStatus.BAD_REQUEST
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            googleWatchService.startGmailWatch(userId)
        );

        assertTrue(exception.getMessage().contains("Failed to start Gmail watch"));
        verify(watchStartCounter).increment();
        verify(watchFailures).increment();
    }

    @Test
    void testStartGmailWatch_RestTemplateException() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenThrow(new RuntimeException("Network error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            googleWatchService.startGmailWatch(userId)
        );

        assertTrue(exception.getMessage().contains("Failed to start Gmail watch"));
        verify(watchStartCounter).increment();
        verify(watchFailures).increment();
    }

    @Test
    void testStopGmailWatch_Success() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        ResponseEntity<String> response = new ResponseEntity<>("", HttpStatus.OK);

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        // When
        Map<String, Object> result = googleWatchService.stopGmailWatch(userId);

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("gmail", result.get("service"));
        assertEquals(userId, result.get("userId"));

        verify(watchStopCounter).increment();
        verify(userOAuthIdentityRepository).findByUserIdAndProvider(userId, "google");
        verify(tokenEncryptionService).decryptToken("encrypted_token");
        verify(restTemplate).postForEntity(
            eq("https://gmail.googleapis.com/gmail/v1/users/me/stop"),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void testStopGmailWatch_NoOAuthIdentity() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            googleWatchService.stopGmailWatch(userId)
        );

        assertTrue(exception.getMessage().contains("No Google OAuth identity found for user"));
        verify(watchStopCounter).increment();
        verify(watchFailures).increment();
        verify(userOAuthIdentityRepository).findByUserIdAndProvider(userId, "google");
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void testStopGmailWatch_ApiError() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"error\":\"Invalid request\"}",
            HttpStatus.BAD_REQUEST
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            googleWatchService.stopGmailWatch(userId)
        );

        assertTrue(exception.getMessage().contains("Failed to stop Gmail watch"));
        verify(watchStopCounter).increment();
        verify(watchFailures).increment();
    }

    @Test
    void testStopGmailWatch_RestTemplateException() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenThrow(new RuntimeException("Network error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            googleWatchService.stopGmailWatch(userId)
        );

        assertTrue(exception.getMessage().contains("Failed to stop Gmail watch"));
        verify(watchStopCounter).increment();
        verify(watchFailures).increment();
    }

    @Test
    void testGetUserOAuthIdentity_Success() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));

        // When
        UserOAuthIdentity result = ReflectionTestUtils.invokeMethod(
            googleWatchService,
            "getUserOAuthIdentity",
            userId
        );

        // Then
        assertNotNull(result);
        assertEquals(oauthIdentity, result);
        assertEquals("google", result.getProvider());
        assertEquals("encrypted_token", result.getAccessTokenEnc());
        verify(userOAuthIdentityRepository).findByUserIdAndProvider(userId, "google");
    }

    @Test
    void testGetUserOAuthIdentity_NotFound() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            ReflectionTestUtils.invokeMethod(
                googleWatchService,
                "getUserOAuthIdentity",
                userId
            )
        );

        assertTrue(exception.getMessage().contains("No Google OAuth identity found for user"));
        verify(userOAuthIdentityRepository).findByUserIdAndProvider(userId, "google");
    }

    @Test
    void testRefreshGmailWatch_Success() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        String responseBody = "{\"historyId\":\"67890\",\"expiration\":\"9876543210\"}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        // When
        Map<String, Object> result = googleWatchService.refreshGmailWatch(userId);

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("gmail", result.get("service"));
        assertEquals(userId, result.get("userId"));
        assertEquals("67890", result.get("historyId"));
        assertEquals("9876543210", result.get("expiration"));

        verify(watchStartCounter).increment();
        verify(userOAuthIdentityRepository).findByUserIdAndProvider(userId, "google");
        verify(tokenEncryptionService).decryptToken("encrypted_token");
    }

    @Test
    void testRefreshGmailWatch_WithLabels() throws Exception {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken("encrypted_token"))
            .thenReturn("decrypted_access_token");

        String responseBody = "{\"historyId\":\"67890\",\"expiration\":\"9876543210\"}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(response);

        // When
        Map<String, Object> result = googleWatchService.refreshGmailWatch(userId, "INBOX", "SENT");

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("67890", result.get("historyId"));

        verify(watchStartCounter).increment();
    }

    @Test
    void testRefreshGmailWatch_Failure() {
        // Given
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            googleWatchService.refreshGmailWatch(userId)
        );

        assertTrue(exception.getMessage().contains("No Google OAuth identity found for user"));
        verify(watchStartCounter).increment();
        verify(watchFailures).increment();
    }
}
