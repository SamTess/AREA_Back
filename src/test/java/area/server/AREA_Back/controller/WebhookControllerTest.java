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
}
