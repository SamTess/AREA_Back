package area.server.AREA_Back.service.Area.Services.Google;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling Google Gmail operations.
 * Provides methods for sending emails, managing labels, and checking for new messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleGmailService {

    private final GoogleApiUtils utils;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Send an email via Gmail API.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including to, subject, body, cc, bcc
     * @return Result map with message_id, thread_id, sent_at
     */
    public Map<String, Object> sendGmail(String token, Map<String, Object> input, Map<String, Object> params) {
        String to = utils.getRequiredParam(params, "to", String.class);
        String subject = utils.getRequiredParam(params, "subject", String.class);
        String body = utils.getRequiredParam(params, "body", String.class);
        String cc = utils.getOptionalParam(params, "cc", String.class, null);
        String bcc = utils.getOptionalParam(params, "bcc", String.class, null);

        StringBuilder emailBuilder = new StringBuilder();
        emailBuilder.append("To: ").append(to).append("\r\n");
        if (cc != null && !cc.isEmpty()) {
            emailBuilder.append("Cc: ").append(cc).append("\r\n");
        }
        if (bcc != null && !bcc.isEmpty()) {
            emailBuilder.append("Bcc: ").append(bcc).append("\r\n");
        }
        emailBuilder.append("Subject: ").append(subject).append("\r\n");
        emailBuilder.append("\r\n");
        emailBuilder.append(body);

        String encodedEmail = Base64.getUrlEncoder().encodeToString(
            emailBuilder.toString().getBytes()
        ).replace("+", "-").replace("/", "_").replace("=", "");

        String url = GoogleApiUtils.GMAIL_API + "/users/me/messages/send";

        Map<String, Object> requestBody = Map.of("raw", encodedEmail);
        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to send Gmail: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("message_id", responseBody.get("id"));
        result.put("thread_id", responseBody.get("threadId"));
        result.put("sent_at", LocalDateTime.now().toString());

        return result;
    }

    /**
     * Add a label to a Gmail message.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including message_id, label_name
     * @return Result map with message_id, labels, updated_at
     */
    public Map<String, Object> addGmailLabel(String token, Map<String, Object> input, Map<String, Object> params) {
        String messageId = utils.getRequiredParam(params, "message_id", String.class);
        String labelName = utils.getRequiredParam(params, "label_name", String.class);

        String url = GoogleApiUtils.GMAIL_API + "/users/me/messages/" + messageId + "/modify";

        Map<String, Object> requestBody = Map.of(
            "addLabelIds", List.of(labelName)
        );

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to add Gmail label: " + response.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message_id", messageId);
        result.put("labels", List.of(labelName));
        result.put("updated_at", LocalDateTime.now().toString());

        return result;
    }

    /**
     * Check for new Gmail messages matching the criteria.
     *
     * @param token Google OAuth token
     * @param params Parameters including label, from, subject_contains
     * @param lastCheck Timestamp of the last check
     * @return List of new messages
     */
    public List<Map<String, Object>> checkNewGmailMessages(String token,
                                                             Map<String, Object> params,
                                                             LocalDateTime lastCheck) {
        String label = utils.getOptionalParam(params, "label", String.class, "INBOX");
        String from = utils.getOptionalParam(params, "from", String.class, null);
        String subjectContains = utils.getOptionalParam(params, "subject_contains", String.class, null);

        StringBuilder query = new StringBuilder("in:" + label);

        String afterDate = lastCheck.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        query.append(" after:").append(afterDate);

        if (from != null && !from.isEmpty()) {
            query.append(" from:").append(from);
        }
        if (subjectContains != null && !subjectContains.isEmpty()) {
            query.append(" subject:(").append(subjectContains).append(")");
        }

        String url = GoogleApiUtils.GMAIL_API + "/users/me/messages?q=" + java.net.URLEncoder.encode(
            query.toString(),
            java.nio.charset.StandardCharsets.UTF_8
        );

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch Gmail messages: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) responseBody.get("messages");

        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        final int maxMessages = 10;
        return messages.stream()
            .limit(maxMessages)
            .map(msg -> fetchGmailMessage(token, (String) msg.get("id")))
            .toList();
    }

    /**
     * Fetch details of a specific Gmail message.
     *
     * @param token Google OAuth token
     * @param messageId The message ID
     * @return Message details map
     */
    private Map<String, Object> fetchGmailMessage(String token, String messageId) {
        String url = GoogleApiUtils.GMAIL_API + "/users/me/messages/" + messageId;

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        Map<String, Object> message = response.getBody();
        Map<String, Object> event = new HashMap<>();
        event.put("message_id", message.get("id"));
        event.put("thread_id", message.get("threadId"));
        event.put("snippet", message.get("snippet"));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emailHeaders = (List<Map<String, Object>>) payload.get("headers");

        for (Map<String, Object> header : emailHeaders) {
            String name = (String) header.get("name");
            String value = (String) header.get("value");
            switch (name.toLowerCase()) {
                case "from":
                    event.put("from", value);
                    break;
                case "to":
                    event.put("to", List.of(value.split(",")));
                    break;
                case "subject":
                    event.put("subject", value);
                    break;
                case "date":
                    event.put("received_at", value);
                    break;
                default:
                    break;
            }
        }

        @SuppressWarnings("unchecked")
        List<String> labelIds = (List<String>) message.get("labelIds");
        if (labelIds != null) {
            event.put("labels", labelIds);
        } else {
            event.put("labels", List.of());
        }

        return event;
    }
}
