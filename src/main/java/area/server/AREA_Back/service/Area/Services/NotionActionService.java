package area.server.AREA_Back.service.Area.Services;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.ServiceAccountService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotionActionService {

    private static final String NOTION_API_BASE = "https://api.notion.com/v1";
    private static final String NOTION_API_VERSION = "2022-06-28";
    private static final String NOTION_PROVIDER_KEY = "notion";

    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final ServiceAccountService serviceAccountService;
    private final RestTemplate restTemplate;

    private final MeterRegistry meterRegistry;
    private Counter notionActionsExecuted;
    private Counter notionActionsFailed;

    @PostConstruct
    public void init() {
        notionActionsExecuted = meterRegistry.counter("notion_actions_executed_total");
        notionActionsFailed = meterRegistry.counter("notion_actions_failed_total");
    }

    /**
     * Execute a Notion action (reaction)
     */
    public Map<String, Object> executeNotionAction(String actionKey,
                                                   Map<String, Object> inputPayload,
                                                   Map<String, Object> actionParams,
                                                   UUID userId) {
        try {
            notionActionsExecuted.increment();

            String notionToken = getNotionToken(userId);
            if (notionToken == null) {
                throw new RuntimeException("No Notion token found for user: " + userId);
            }

            switch (actionKey) {
                case "create_page":
                    return createPage(notionToken, inputPayload, actionParams);
                case "update_page":
                    return updatePage(notionToken, inputPayload, actionParams);
                case "create_database_item":
                    return createDatabaseItem(notionToken, inputPayload, actionParams);
                case "update_database_item":
                    return updateDatabaseItem(notionToken, inputPayload, actionParams);
                case "archive_page":
                    return archivePage(notionToken, inputPayload, actionParams);
                case "add_comment":
                    return addComment(notionToken, inputPayload, actionParams);
                case "create_database":
                    return createDatabase(notionToken, inputPayload, actionParams);
                default:
                    throw new IllegalArgumentException("Unknown Notion action: " + actionKey);
            }
        } catch (Exception e) {
            notionActionsFailed.increment();
            log.error("Failed to execute Notion action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Notion action execution failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> createPage(String token, Map<String, Object> input, Map<String, Object> params) {
        String pageId = getRequiredParam(params, "page_id", String.class);
        String title = getRequiredParam(params, "title", String.class);
        String content = getOptionalParam(params, "content", String.class, "");

        String url = NOTION_API_BASE + "/pages";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("parent", Map.of("page_id", pageId));

        requestBody.put("properties", Map.of(
            "title", Map.of(
                "title", List.of(Map.of("text", Map.of("content", title)))
            )
        ));

        if (!content.isBlank()) {
            requestBody.put("children", List.of(
                Map.of(
                    "object", "block",
                    "type", "paragraph",
                    "paragraph", Map.of(
                        "rich_text", List.of(Map.of("text", Map.of("content", content)))
                    )
                )
            ));
        }

        HttpHeaders headers = createNotionHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("page_id", responseBody.get("id"));
        result.put("url", responseBody.get("url"));

        return result;
    }

    private Map<String, Object> updatePage(String token, Map<String, Object> input, Map<String, Object> params) {
        String pageId = getRequiredParam(params, "page_id", String.class);
        String title = getOptionalParam(params, "title", String.class, null);
        String content = getOptionalParam(params, "content", String.class, null);

        String url = NOTION_API_BASE + "/pages/" + pageId;

        Map<String, Object> requestBody = new HashMap<>();

        if (title != null && !title.isBlank()) {
            requestBody.put("properties", Map.of(
                "title", Map.of(
                    "title", List.of(Map.of("text", Map.of("content", title)))
                )
            ));
        }

        log.info("Updating Notion page {} with body: {}", pageId, requestBody);

        HttpHeaders headers = createNotionHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.PATCH, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        log.info("Notion response: {}", responseBody);

        if (content != null && !content.isBlank()) {
            String blockUrl = NOTION_API_BASE + "/blocks/" + pageId + "/children";
            Map<String, Object> blockBody = Map.of(
                "children", List.of(
                    Map.of(
                        "object", "block",
                        "type", "paragraph",
                        "paragraph", Map.of(
                            "rich_text", List.of(Map.of("text", Map.of("content", content)))
                        )
                    )
                )
            );

            HttpEntity<Map<String, Object>> blockRequest = new HttpEntity<>(blockBody, headers);
            restTemplate.exchange(
                blockUrl, HttpMethod.PATCH, blockRequest,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );
        }

        Map<String, Object> result = new HashMap<>();
        result.put("page_id", responseBody.get("id"));
        result.put("url", responseBody.get("url"));

        return result;
    }

    private Map<String, Object> createDatabaseItem(String token, Map<String, Object> input,
                                                   Map<String, Object> params) {
        String databaseId = getRequiredParam(params, "database_id", String.class);
        String name = getRequiredParam(params, "name", String.class);

        String url = NOTION_API_BASE + "/pages";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("parent", Map.of("database_id", databaseId));
        requestBody.put("properties", Map.of(
            "Name", Map.of(
                "title", List.of(Map.of("text", Map.of("content", name)))
            )
        ));

        HttpHeaders headers = createNotionHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("page_id", responseBody.get("id"));
        result.put("url", responseBody.get("url"));

        return result;
    }

    private Map<String, Object> updateDatabaseItem(String token, Map<String, Object> input,
                                                   Map<String, Object> params) {
        String pageId = getRequiredParam(params, "page_id", String.class);
        String name = getRequiredParam(params, "name", String.class);

        String url = NOTION_API_BASE + "/pages/" + pageId;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("properties", Map.of(
            "Name", Map.of(
                "title", List.of(Map.of("text", Map.of("content", name)))
            )
        ));

        HttpHeaders headers = createNotionHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.PATCH, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("page_id", responseBody.get("id"));
        result.put("url", responseBody.get("url"));

        return result;
    }

    private Map<String, Object> archivePage(String token, Map<String, Object> input, Map<String, Object> params) {
        String pageId = getRequiredParam(params, "page_id", String.class);

        String url = NOTION_API_BASE + "/pages/" + pageId;

        Map<String, Object> requestBody = Map.of("archived", true);

        HttpHeaders headers = createNotionHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.PATCH, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("page_id", responseBody.get("id"));
        result.put("archived", true);

        return result;
    }

    private Map<String, Object> addComment(String token, Map<String, Object> input, Map<String, Object> params) {
        String pageId = getRequiredParam(params, "page_id", String.class);
        String comment = getRequiredParam(params, "comment", String.class);

        String url = NOTION_API_BASE + "/comments";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("parent", Map.of("page_id", pageId));
        requestBody.put("rich_text", List.of(
            Map.of("text", Map.of("content", comment))
        ));

        HttpHeaders headers = createNotionHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("comment_id", responseBody.get("id"));

        return result;
    }

    private Map<String, Object> createDatabase(String token, Map<String, Object> input, Map<String, Object> params) {
        String pageId = getRequiredParam(params, "page_id", String.class);
        String title = getRequiredParam(params, "title", String.class);

        String url = NOTION_API_BASE + "/databases";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("parent", Map.of("type", "page_id", "page_id", pageId));
        requestBody.put("title", List.of(Map.of("text", Map.of("content", title))));
        requestBody.put("properties", Map.of(
            "Name", Map.of("title", new HashMap<>())
        ));

        HttpHeaders headers = createNotionHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("database_id", responseBody.get("id"));
        result.put("url", responseBody.get("url"));

        return result;
    }

    private String getNotionToken(UUID userId) {
        Optional<String> serviceToken = serviceAccountService.getAccessToken(userId, NOTION_PROVIDER_KEY);
        if (serviceToken.isPresent()) {
            log.debug("Notion token found in service accounts for user: {}", userId);
            return serviceToken.get();
        }

        Optional<area.server.AREA_Back.entity.User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", userId);
            return null;
        }

        Optional<UserOAuthIdentity> oauthOpt = userOAuthIdentityRepository
            .findByUserAndProvider(userOpt.get(), NOTION_PROVIDER_KEY);
        if (oauthOpt.isEmpty()) {
            log.warn("No Notion OAuth identity found for user: {}", userId);
            return null;
        }

        UserOAuthIdentity oauth = oauthOpt.get();
        String encryptedToken = oauth.getAccessTokenEnc();
        if (encryptedToken == null || encryptedToken.isBlank()) {
            log.warn("Notion token is null or empty for user: {}", userId);
            return null;
        }

        try {
            String decryptedToken = tokenEncryptionService.decryptToken(encryptedToken);
            log.debug("Notion token successfully decrypted for user: {}", userId);
            return decryptedToken;
        } catch (Exception e) {
            log.error("Error decrypting Notion token for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private HttpHeaders createNotionHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Notion-Version", NOTION_API_VERSION);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private <T> T getRequiredParam(Map<String, Object> params, String key, Class<T> type) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Parameter " + key + " must be of type " + type.getSimpleName());
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    private <T> T getOptionalParam(Map<String, Object> params, String key, Class<T> type, T defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!type.isInstance(value)) {
            return defaultValue;
        }
        return (T) value;
    }
}
