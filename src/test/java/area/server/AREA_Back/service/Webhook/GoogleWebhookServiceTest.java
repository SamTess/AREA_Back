package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleWebhookServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private WebhookEventProcessingService webhookEventProcessingService;

    @Mock
    private WebhookDeduplicationService deduplicationService;

    @Mock
    private Counter webhookCounter;

    @Mock
    private Counter gmailEventCounter;

    @Mock
    private Counter webhookProcessingFailures;

    private GoogleWebhookService googleWebhookService;
    private ObjectMapper objectMapper;

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
        googleWebhookService = new GoogleWebhookService(
            meterRegistry,
            userOAuthIdentityRepository,
            userRepository,
            tokenEncryptionService,
            restTemplate,
            objectMapper,
            webhookEventProcessingService,
            deduplicationService
        );

        lenient().when(meterRegistry.counter(anyString())).thenReturn(webhookCounter);

        // Inject mock counters
        ReflectionTestUtils.setField(googleWebhookService, "webhookCounter", webhookCounter);
        ReflectionTestUtils.setField(googleWebhookService, "gmailEventCounter", gmailEventCounter);
        ReflectionTestUtils.setField(googleWebhookService, "webhookProcessingFailures", webhookProcessingFailures);
    }

    @Test
    void testInitMetrics() {
        GoogleWebhookService newService = new GoogleWebhookService(
            meterRegistry,
            userOAuthIdentityRepository,
            userRepository,
            tokenEncryptionService,
            restTemplate,
            objectMapper,
            webhookEventProcessingService,
            deduplicationService
        );

        when(meterRegistry.counter(anyString())).thenReturn(webhookCounter);

        newService.initMetrics();

        assertNotNull(newService);
    }

    @Test
    void testProcessWebhook_ValidGmailEvent() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(deduplicationService.checkAndMark(anyString(), eq("google"))).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn("decrypted_token");

        String gmailResponse = "{\"messages\":[{\"id\":\"msg123\",\"threadId\":\"thread123\"}]}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(gmailResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(responseEntity);

        List<Execution> executions = Arrays.asList(new Execution());
        when(webhookEventProcessingService.processWebhookEventForUser(
            eq("google"), eq("gmail_new_email"), anyMap(), eq(userId)
        )).thenReturn(executions);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("gmail", result.get("serviceType"));
        verify(webhookCounter, atLeastOnce()).increment();
        verify(gmailEventCounter, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_MissingMessage() {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertTrue(result.get("error").toString().contains("Missing Pub/Sub message"));
        verify(webhookProcessingFailures, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_MissingData() {
        Map<String, Object> pubSubMessage = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertTrue(result.get("error").toString().contains("Missing data"));
        verify(webhookProcessingFailures, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_InvalidBase64Data() {
        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", "invalid_base64!");

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("error", result.get("status"));
        verify(webhookProcessingFailures, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_JsonParsingError() throws Exception {
        String encodedData = Base64.getEncoder().encodeToString("invalid json".getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("error", result.get("status"));
        verify(webhookProcessingFailures, atLeastOnce()).increment();
    }

    @Test
    void testDetermineServiceType_Gmail() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertEquals("gmail", result.get("serviceType"));
    }

    @Test
    void testDetermineServiceType_Drive() throws Exception {
        String notificationJson = "{\"resourceId\":\"123\",\"resourceUri\":\"https://drive.google.com/file/123\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertEquals("drive", result.get("serviceType"));
    }

    @Test
    void testDetermineServiceType_Calendar() throws Exception {
        String notificationJson = "{\"resourceId\":\"123\",\"resourceUri\":\"https://calendar.google.com/calendar/123\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertEquals("calendar", result.get("serviceType"));
    }

    @Test
    void testDetermineServiceType_Unknown() throws Exception {
        String notificationJson = "{\"someField\":\"someValue\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertEquals("unknown", result.get("serviceType"));
    }

    @Test
    void testProcessGmailEvent_WithValidData() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(deduplicationService.checkAndMark(anyString(), eq("google"))).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn("decrypted_token");

        String gmailResponse = "{\"messages\":[{\"id\":\"msg123\",\"threadId\":\"thread123\"}]}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(gmailResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(responseEntity);

        List<Execution> executions = Arrays.asList(new Execution());
        when(webhookEventProcessingService.processWebhookEventForUser(
            eq("google"), eq("gmail_new_email"), anyMap(), eq(userId)
        )).thenReturn(executions);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        verify(gmailEventCounter, atLeastOnce()).increment();
    }

    @Test
    void testProcessGmailEvent_DuplicateEvent() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(deduplicationService.checkAndMark(anyString(), eq("google"))).thenReturn(true);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        verify(webhookEventProcessingService, never()).processWebhookEventForUser(
            anyString(), anyString(), anyMap(), any(UUID.class)
        );
    }

    @Test
    void testProcessGmailEvent_UserNotFound() throws Exception {
        String notificationJson = "{\"emailAddress\":\"notfound@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("notfound@gmail.com")).thenReturn(Optional.empty());

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        verify(webhookEventProcessingService, never()).processWebhookEventForUser(
            anyString(), anyString(), anyMap(), any(UUID.class)
        );
    }

    @Test
    void testProcessGmailEvent_NoOAuthIdentity() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.empty());

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        verify(webhookEventProcessingService, never()).processWebhookEventForUser(
            anyString(), anyString(), anyMap(), any(UUID.class)
        );
    }

    @Test
    void testProcessGmailEvent_NullEmailAddress() throws Exception {
        String notificationJson = "{\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        assertEquals("gmail", result.get("serviceType"));
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void testFindUserByGmailAddress_Found() throws Exception {
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));

        googleWebhookService.processWebhook(payload);

        verify(userRepository).findByEmail("test@gmail.com");
    }

    @Test
    void testFindUserByGmailAddress_NotFound() throws Exception {
        when(userRepository.findByEmail("notfound@gmail.com")).thenReturn(Optional.empty());

        String notificationJson = "{\"emailAddress\":\"notfound@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        googleWebhookService.processWebhook(payload);

        verify(userRepository).findByEmail("notfound@gmail.com");
    }

    @Test
    void testFetchAndProcessNewGmailMessages_NoMessages() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(deduplicationService.checkAndMark(anyString(), eq("google"))).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn("decrypted_token");

        String gmailResponse = "{}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(gmailResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(responseEntity);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        verify(webhookEventProcessingService, never()).processWebhookEventForUser(
            anyString(), anyString(), anyMap(), any(UUID.class)
        );
    }

    @Test
    void testFetchAndProcessNewGmailMessages_DuplicateMessage() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));

        when(deduplicationService.checkAndMark(contains("gmail_" + userId), eq("google")))
            .thenReturn(false);
        when(deduplicationService.checkAndMark(contains("gmail_message_"), eq("google")))
            .thenReturn(true);

        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn("decrypted_token");

        String gmailResponse = "{\"messages\":[{\"id\":\"msg123\",\"threadId\":\"thread123\"}]}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(gmailResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(responseEntity);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        verify(webhookEventProcessingService, never()).processWebhookEventForUser(
            anyString(), anyString(), anyMap(), any(UUID.class)
        );
    }

    @Test
    void testFetchAndProcessNewGmailMessages_ApiError() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(deduplicationService.checkAndMark(anyString(), eq("google"))).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn("decrypted_token");

        ResponseEntity<String> responseEntity = new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(responseEntity);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        verify(webhookEventProcessingService, never()).processWebhookEventForUser(
            anyString(), anyString(), anyMap(), any(UUID.class)
        );
    }

    @Test
    void testFetchAndProcessNewGmailMessages_Exception() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(deduplicationService.checkAndMark(anyString(), eq("google"))).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn("decrypted_token");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("API Error"));

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        verify(webhookEventProcessingService, never()).processWebhookEventForUser(
            anyString(), anyString(), anyMap(), any(UUID.class)
        );
    }

    @Test
    void testFetchAndProcessNewGmailMessages_MessageWithoutThreadId() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(deduplicationService.checkAndMark(anyString(), eq("google"))).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn("decrypted_token");

        String gmailResponse = "{\"messages\":[{\"id\":\"msg123\"}]}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(gmailResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(responseEntity);

        List<Execution> executions = Arrays.asList(new Execution());
        when(webhookEventProcessingService.processWebhookEventForUser(
            eq("google"), eq("gmail_new_email"), anyMap(), eq(userId)
        )).thenReturn(executions);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        verify(webhookEventProcessingService).processWebhookEventForUser(
            eq("google"), eq("gmail_new_email"), anyMap(), eq(userId)
        );
    }

    @Test
    void testFetchAndProcessNewGmailMessages_MultipleMessages_StopsAfterFirst() throws Exception {
        String notificationJson = "{\"emailAddress\":\"test@gmail.com\",\"historyId\":\"12345\"}";
        String encodedData = Base64.getEncoder().encodeToString(notificationJson.getBytes());

        Map<String, Object> pubSubMessage = new HashMap<>();
        pubSubMessage.put("data", encodedData);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", pubSubMessage);

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserIdAndProvider(userId, "google"))
            .thenReturn(Optional.of(oauthIdentity));
        when(deduplicationService.checkAndMark(anyString(), eq("google"))).thenReturn(false);
        when(tokenEncryptionService.decryptToken("encrypted_token")).thenReturn("decrypted_token");

        String gmailResponse = "{\"messages\":[{\"id\":\"msg1\",\"threadId\":\"t1\"},{\"id\":\"msg2\",\"threadId\":\"t2\"}]}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(gmailResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(responseEntity);

        List<Execution> executions = Arrays.asList(new Execution());
        when(webhookEventProcessingService.processWebhookEventForUser(
            eq("google"), eq("gmail_new_email"), anyMap(), eq(userId)
        )).thenReturn(executions);

        Map<String, Object> result = googleWebhookService.processWebhook(payload);

        assertNotNull(result);
        // Should only process first message
        verify(webhookEventProcessingService, times(1)).processWebhookEventForUser(
            eq("google"), eq("gmail_new_email"), anyMap(), eq(userId)
        );
    }
}
