package area.server.AREA_Back.controller;

import area.server.AREA_Back.service.Webhook.GoogleWatchService;
import area.server.AREA_Back.service.Webhook.GoogleWebhookService;
import area.server.AREA_Back.service.Webhook.SlackWebhookService;
import area.server.AREA_Back.service.Webhook.WebhookDeduplicationService;
import area.server.AREA_Back.service.Webhook.WebhookEventProcessingService;
import area.server.AREA_Back.service.Webhook.WebhookSecretService;
import area.server.AREA_Back.service.Webhook.WebhookSignatureValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookController Tests")
class WebhookControllerTest {

    @Mock
    private GoogleWebhookService googleWebhookService;

    @Mock
    private GoogleWatchService googleWatchService;

    @Mock
    private SlackWebhookService slackWebhookService;

    @Mock
    private WebhookSignatureValidator signatureValidator;

    @Mock
    private WebhookDeduplicationService deduplicationService;

    @Mock
    private WebhookEventProcessingService eventProcessingService;

    @Mock
    private WebhookSecretService webhookSecretService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WebhookController webhookController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController).build();
    }

    @Test
    void slackUrlVerificationReturnsChallenge() throws Exception {
        Map<String, Object> payload = Map.of("type", "url_verification", "challenge", "abc123");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(slackWebhookService.processWebhook(payload)).thenReturn(Map.of("challenge", "abc123"));

        mockMvc.perform(post("/api/hooks/slack/whatever").content("{\"type\":\"url_verification\"}")
                        .header("Content-Type", "application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("abc123"));

        verify(slackWebhookService, times(1)).processWebhook(anyMap());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(webhookSecretService.getServiceSecret(anyString())).thenReturn("secret");
        when(signatureValidator.validateSignature(anyString(), any(), anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/github/issue").content("{}")
                        .header("X-Hub-Signature-256", "invalid-signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid signature"));
    }

    @Test
    void duplicateWebhookReturnsOkDuplicateStatus() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        // No signature validation needed (no secret)
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/hooks/github/issue").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));
        
        verify(deduplicationService).checkAndMark(anyString(), anyString());
    }

    @Test
    void unknownUserReturnsUnprocessableEntity() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        // For unknown service without userId param, controller cannot identify user and returns 422
        mockMvc.perform(post("/api/hooks/unknown/action").content("{}"))
                .andExpect(status().isUnprocessableEntity());
        
        verify(deduplicationService).checkAndMark(anyString(), anyString());
    }

    @Test
    void startGmailWatchSuccess() throws Exception {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> result = Map.of("status", "success", "historyId", "123");
        
        when(googleWatchService.startGmailWatch(any(UUID.class), any())).thenReturn(result);

        mockMvc.perform(post("/api/hooks/google/gmail/watch/start")
                        .param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(googleWatchService).startGmailWatch(any(UUID.class), any());
    }

    @Test
    void startGmailWatchWithLabels() throws Exception {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> result = Map.of("status", "success");
        
        when(googleWatchService.startGmailWatch(any(UUID.class), any(String[].class))).thenReturn(result);

        mockMvc.perform(post("/api/hooks/google/gmail/watch/start")
                        .param("userId", userId)
                        .param("labelIds", "INBOX", "UNREAD"))
                .andExpect(status().isOk());

        verify(googleWatchService).startGmailWatch(any(UUID.class), any(String[].class));
    }

    @Test
    void startGmailWatchFailure() throws Exception {
        String userId = UUID.randomUUID().toString();
        
        when(googleWatchService.startGmailWatch(any(UUID.class), any()))
                .thenThrow(new RuntimeException("Watch failed"));

        mockMvc.perform(post("/api/hooks/google/gmail/watch/start")
                        .param("userId", userId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to start Gmail watch"));
    }

    @Test
    void stopGmailWatchSuccess() throws Exception {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> result = Map.of("status", "stopped");
        
        when(googleWatchService.stopGmailWatch(any(UUID.class))).thenReturn(result);

        mockMvc.perform(post("/api/hooks/google/gmail/watch/stop")
                        .param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stopped"));

        verify(googleWatchService).stopGmailWatch(any(UUID.class));
    }

    @Test
    void stopGmailWatchFailure() throws Exception {
        String userId = UUID.randomUUID().toString();
        
        when(googleWatchService.stopGmailWatch(any(UUID.class)))
                .thenThrow(new RuntimeException("Stop failed"));

        mockMvc.perform(post("/api/hooks/google/gmail/watch/stop")
                        .param("userId", userId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to stop Gmail watch"));
    }

    @Test
    void webhookWithUserIdParameter() throws Exception {
        UUID userId = UUID.randomUUID();
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), eq(userId)))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", userId.toString())
                        .content("{}"))
                .andExpect(status().isOk());

        verify(eventProcessingService).processWebhookEventForUser(anyString(), anyString(), anyMap(), eq(userId));
    }

    @Test
    void refreshGmailWatchSuccess() throws Exception {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> result = Map.of("status", "refreshed", "historyId", "456");
        
        when(googleWatchService.refreshGmailWatch(any(UUID.class), any(String[].class))).thenReturn(result);

        mockMvc.perform(post("/api/hooks/google/gmail/watch/refresh")
                        .param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("refreshed"));

        verify(googleWatchService).refreshGmailWatch(any(UUID.class), any(String[].class));
    }

    @Test
    void refreshGmailWatchWithLabels() throws Exception {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> result = Map.of("status", "refreshed");
        
        when(googleWatchService.refreshGmailWatch(any(UUID.class), any(String[].class))).thenReturn(result);

        mockMvc.perform(post("/api/hooks/google/gmail/watch/refresh")
                        .param("userId", userId)
                        .param("labelIds", "INBOX", "SENT"))
                .andExpect(status().isOk());

        verify(googleWatchService).refreshGmailWatch(any(UUID.class), any(String[].class));
    }

    @Test
    void refreshGmailWatchFailure() throws Exception {
        String userId = UUID.randomUUID().toString();
        
        when(googleWatchService.refreshGmailWatch(any(UUID.class), any(String[].class)))
                .thenThrow(new RuntimeException("Refresh failed"));

        mockMvc.perform(post("/api/hooks/google/gmail/watch/refresh")
                        .param("userId", userId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to refresh Gmail watch"));
    }

    @Test
    void handleWebhookWithGitHubEventId() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .header("X-GitHub-Delivery", "github-event-123")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void handleWebhookWithGoogleEventId() throws Exception {
        Map<String, Object> payload = Map.of(
            "message", Map.of(
                "messageId", "google-msg-123",
                "data", "base64data"
            )
        );
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(googleWebhookService.processWebhook(payload)).thenReturn(Map.of("status", "processed"));

        mockMvc.perform(post("/api/hooks/google/push")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"));
    }

    @Test
    void handleWebhookWithSlackEventId() throws Exception {
        Map<String, Object> payload = Map.of(
            "event_id", "slack-evt-123",
            "type", "event_callback"
        );
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(slackWebhookService.processWebhook(payload)).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/api/hooks/slack/event")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"));
    }

    @Test
    void handleWebhookWithUnknownService() throws Exception {
        Map<String, Object> payload = Map.of("data", "test");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/unknown-service/action")
                        .param("userId", UUID.randomUUID().toString())
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void handleWebhookWithInvalidJson() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class)))
                .thenReturn(Map.of()); // parseJsonBody catches IOException and returns empty map
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), any(UUID.class)))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .content("invalid-json"))
                .andExpect(status().isOk());
    }

    @Test
    void handleWebhookWithGitHubUserIdentification() throws Exception {
        Map<String, Object> payload = Map.of(
            "repository", Map.of(
                "owner", Map.of(
                    "login", "testuser"
                )
            )
        );
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/github/issue")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void handleWebhookProcessingException() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), any()))
                .thenThrow(new RuntimeException("Processing failed"));

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Webhook processing failed: Processing failed"));
    }

    @Test
    void validateSignatureWithGitHubService() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(webhookSecretService.getServiceSecret("github")).thenReturn("secret123");
        when(signatureValidator.validateSignature(eq("github"), any(), anyString(), eq("secret123"), isNull()))
                .thenReturn(true);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .header("X-Hub-Signature-256", "sha256=abc123")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(signatureValidator).validateSignature(eq("github"), any(), anyString(), eq("secret123"), isNull());
    }

    @Test
    void validateSignatureWithSlackService() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of("type", "event_callback"));
        when(webhookSecretService.getServiceSecret("slack")).thenReturn("slack-secret");
        when(signatureValidator.validateSignature(eq("slack"), any(), anyString(), eq("slack-secret"), anyString()))
                .thenReturn(true);
        when(slackWebhookService.processWebhook(anyMap())).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/api/hooks/slack/event")
                        .header("X-Slack-Signature", "v0=signature")
                        .header("X-Slack-Request-Timestamp", "1234567890")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(signatureValidator).validateSignature(eq("slack"), any(), anyString(), eq("slack-secret"), anyString());
    }

    @Test
    void validateSignatureForUnknownService() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(webhookSecretService.getServiceSecret("custom")).thenReturn("custom-secret");
        when(signatureValidator.validateSignature(eq("custom"), any(), anyString(), eq("custom-secret"), isNull()))
                .thenReturn(true);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/hooks/custom/action")
                        .param("userId", UUID.randomUUID().toString())
                        .header("X-Signature", "custom-sig")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void validateSignatureWithNoSecret() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(webhookSecretService.getServiceSecret("github")).thenReturn(null);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .header("X-Hub-Signature-256", "sha256=abc123")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void validateSignatureWithNoSignatureHeader() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void validateSignatureThrowsException() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(webhookSecretService.getServiceSecret("github")).thenReturn("secret");
        when(signatureValidator.validateSignature(anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Validation error"));

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .header("X-Hub-Signature-256", "sha256=abc123")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSignatureHeaderForGoogle() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(googleWebhookService.processWebhook(anyMap())).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/api/hooks/google/push")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void handleWebhookWithExecutions() throws Exception {
        UUID userId = UUID.randomUUID();
        area.server.AREA_Back.entity.Execution exec1 = new area.server.AREA_Back.entity.Execution();
        exec1.setId(UUID.randomUUID());
        area.server.AREA_Back.entity.Execution exec2 = new area.server.AREA_Back.entity.Execution();
        exec2.setId(UUID.randomUUID());
        
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(Map.of());
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), eq(userId)))
                .thenReturn(List.of(exec1, exec2));

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", userId.toString())
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionsCreated").value(2))
                .andExpect(jsonPath("$.executionIds.length()").value(2));
    }

    @Test
    void extractEventIdWithNoHeaders() throws Exception {
        Map<String, Object> payload = Map.of("data", "test");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(eventProcessingService.processWebhookEventForUser(anyString(), anyString(), anyMap(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/hooks/custom/action")
                        .param("userId", UUID.randomUUID().toString())
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void extractEventIdForGoogleWithoutMessage() throws Exception {
        Map<String, Object> payload = Map.of("data", "test");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(googleWebhookService.processWebhook(payload)).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/api/hooks/google/action")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void extractEventIdForGoogleWithoutMessageId() throws Exception {
        Map<String, Object> payload = Map.of(
            "message", Map.of("data", "base64")
        );
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(googleWebhookService.processWebhook(payload)).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/api/hooks/google/action")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void identifyGitHubUserWithNoRepository() throws Exception {
        Map<String, Object> payload = Map.of("action", "opened");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/github/issue")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void identifyGitHubUserWithNoOwner() throws Exception {
        Map<String, Object> payload = Map.of(
            "repository", Map.of("name", "test-repo")
        );
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/github/issue")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void identifyGitHubUserWithNoLogin() throws Exception {
        Map<String, Object> payload = Map.of(
            "repository", Map.of(
                "owner", Map.of("type", "User")
            )
        );
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/github/issue")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void handleGoogleWebhookWithoutUserId() throws Exception {
        Map<String, Object> payload = Map.of(
            "message", Map.of(
                "messageId", "google-msg-456",
                "data", "base64data"
            )
        );
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(googleWebhookService.processWebhook(payload))
                .thenReturn(Map.of("status", "processed", "historyId", "789"));

        mockMvc.perform(post("/api/hooks/google/push")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"))
                .andExpect(jsonPath("$.service").value("google"))
                .andExpect(jsonPath("$.serviceResult.status").value("processed"));

        verify(googleWebhookService).processWebhook(payload);
    }

    @Test
    void handleSlackWebhookWithoutUserId() throws Exception {
        Map<String, Object> payload = Map.of(
            "event_id", "slack-evt-789",
            "type", "event_callback",
            "event", Map.of("type", "message")
        );
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(slackWebhookService.processWebhook(payload))
                .thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/api/hooks/slack/event")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"))
                .andExpect(jsonPath("$.service").value("slack"))
                .andExpect(jsonPath("$.serviceResult.status").value("ok"));

        verify(slackWebhookService).processWebhook(payload);
    }

    @Test
    void parseJsonBodyWithEmptyArray() throws Exception {
        // When body is empty, parseJsonBody returns empty map without calling objectMapper
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .content(""))
                .andExpect(status().isOk());
    }

    @Test
    void parseJsonBodyWithIOException() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(Map.class)))
                .thenThrow(new IOException("Invalid JSON"));
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/github/issue")
                        .param("userId", UUID.randomUUID().toString())
                        .content("{invalid json"))
                .andExpect(status().isOk());
    }

    @Test
    void handleWebhookWithServiceResultNotEmpty() throws Exception {
        Map<String, Object> payload = Map.of(
            "message", Map.of(
                "messageId", "msg-123",
                "data", "test-data"
            )
        );
        Map<String, Object> serviceResult = Map.of(
            "status", "success",
            "details", "processed"
        );

        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);
        when(googleWebhookService.processWebhook(payload)).thenReturn(serviceResult);

        mockMvc.perform(post("/api/hooks/google/action")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceResult.status").value("success"))
                .andExpect(jsonPath("$.serviceResult.details").value("processed"));
    }

    @Test
    void handleGitHubWebhookIdentifyUserFails() throws Exception {
        Map<String, Object> payload = Map.of(
            "repository", Map.of(
                "owner", Map.of("login", "unknownuser")
            ),
            "action", "created"
        );

        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(payload);
        when(deduplicationService.checkAndMark(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/hooks/github/issue")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("User identification failed"));
    }
}
