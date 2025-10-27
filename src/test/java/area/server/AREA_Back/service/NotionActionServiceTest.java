package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Area.Services.NotionActionService;
import area.server.AREA_Back.service.Auth.ServiceAccountService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class NotionActionServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private ServiceAccountService serviceAccountService;

    @Mock
    private RestTemplate restTemplate;

    private SimpleMeterRegistry meterRegistry;
    private NotionActionService notionActionService;

    private UUID testUserId;
    private String testToken;
    private User testUser;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        notionActionService = new NotionActionService(
            userOAuthIdentityRepository,
            userRepository,
            tokenEncryptionService,
            serviceAccountService,
            restTemplate,
            meterRegistry
        );
        notionActionService.init();

        testUserId = UUID.randomUUID();
        testToken = "secret_test_token_123";
        testUser = new User();
        testUser.setId(testUserId);
    }

    // ==================== Test init() ====================
    @Test
    void testServiceInitialization() {
        assertNotNull(notionActionService);
        assertNotNull(meterRegistry);
        assertEquals(0.0, meterRegistry.counter("notion_actions_executed_total").count());
        assertEquals(0.0, meterRegistry.counter("notion_actions_failed_total").count());
    }

    // ==================== Test createNotionHeaders() ====================
    @Test
    void testCreateNotionHeaders() {
        Map<String, Object> params = Map.of("page_id", "test-page-id", "title", "Test");
        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        setupMockTokenFromServiceAccount();
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        notionActionService.executeNotionAction("create_page", Map.of(), params, testUserId);

        verify(restTemplate).exchange(
            anyString(),
            eq(HttpMethod.POST),
            argThat(entity -> {
                HttpEntity<Map<String, Object>> httpEntity = (HttpEntity<Map<String, Object>>) entity;
                return httpEntity.getHeaders().getFirst("Authorization").equals("Bearer " + testToken) &&
                       httpEntity.getHeaders().getFirst("Notion-Version").equals("2022-06-28") &&
                       httpEntity.getHeaders().getContentType().toString().contains("application/json");
            }),
            any(ParameterizedTypeReference.class)
        );
    }

    // ==================== Test getNotionToken() - Service Account ====================
    @Test
    void testGetNotionTokenFromServiceAccount() {
        when(serviceAccountService.getAccessToken(testUserId, "notion"))
            .thenReturn(Optional.of(testToken));

        Map<String, Object> params = Map.of("page_id", "test-page-id", "title", "Test");
        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_page", Map.of(), params, testUserId
        );

        verify(serviceAccountService).getAccessToken(testUserId, "notion");
        assertNotNull(result);
        assertEquals("page-123", result.get("page_id"));
    }

    // ==================== Test getNotionToken() - OAuth Identity ====================
    @Test
    void testGetNotionTokenFromOAuthIdentity() {
        when(serviceAccountService.getAccessToken(testUserId, "notion"))
            .thenReturn(Optional.empty());

        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("encrypted-token");

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userOAuthIdentityRepository.findByUserAndProvider(testUser, "notion"))
            .thenReturn(Optional.of(identity));
        when(tokenEncryptionService.decryptToken("encrypted-token"))
            .thenReturn(testToken);

        Map<String, Object> params = Map.of("page_id", "test-page-id", "title", "Test");
        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_page", Map.of(), params, testUserId
        );

        verify(tokenEncryptionService).decryptToken("encrypted-token");
        assertNotNull(result);
    }

    // ==================== Test getNotionToken() - User Not Found ====================
    @Test
    void testGetNotionTokenUserNotFound() {
        when(serviceAccountService.getAccessToken(testUserId, "notion"))
            .thenReturn(Optional.empty());
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notionActionService.executeNotionAction("create_page", Map.of(), Map.of(), testUserId);
        });

        assertTrue(exception.getMessage().contains("No Notion token found"));
    }

    // ==================== Test getNotionToken() - No OAuth Identity ====================
    @Test
    void testGetNotionTokenNoOAuthIdentity() {
        when(serviceAccountService.getAccessToken(testUserId, "notion"))
            .thenReturn(Optional.empty());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userOAuthIdentityRepository.findByUserAndProvider(testUser, "notion"))
            .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notionActionService.executeNotionAction("create_page", Map.of(), Map.of(), testUserId);
        });

        assertTrue(exception.getMessage().contains("No Notion token found"));
    }

    // ==================== Test getNotionToken() - Empty Token ====================
    @Test
    void testGetNotionTokenEmptyToken() {
        when(serviceAccountService.getAccessToken(testUserId, "notion"))
            .thenReturn(Optional.empty());

        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("");

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userOAuthIdentityRepository.findByUserAndProvider(testUser, "notion"))
            .thenReturn(Optional.of(identity));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notionActionService.executeNotionAction("create_page", Map.of(), Map.of(), testUserId);
        });

        assertTrue(exception.getMessage().contains("No Notion token found"));
    }

    // ==================== Test getNotionToken() - Decryption Error ====================
    @Test
    void testGetNotionTokenDecryptionError() {
        when(serviceAccountService.getAccessToken(testUserId, "notion"))
            .thenReturn(Optional.empty());

        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc("encrypted-token");

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userOAuthIdentityRepository.findByUserAndProvider(testUser, "notion"))
            .thenReturn(Optional.of(identity));
        when(tokenEncryptionService.decryptToken("encrypted-token"))
            .thenThrow(new RuntimeException("Decryption failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notionActionService.executeNotionAction("create_page", Map.of(), Map.of(), testUserId);
        });

        assertTrue(exception.getMessage().contains("No Notion token found"));
    }

    // ==================== Test executeNotionAction() - Unknown Action ====================
    @Test
    void testExecuteNotionActionUnknownAction() {
        setupMockTokenFromServiceAccount();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notionActionService.executeNotionAction("unknown_action", Map.of(), Map.of(), testUserId);
        });

        assertTrue(exception.getMessage().contains("Unknown Notion action"));
        assertEquals(1.0, meterRegistry.counter("notion_actions_failed_total").count());
    }

    // ==================== Test executeNotionAction() - Increments Counter ====================
    @Test
    void testExecuteNotionActionIncrementsCounter() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of("page_id", "test-page-id", "title", "Test");
        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        notionActionService.executeNotionAction("create_page", Map.of(), params, testUserId);

        assertEquals(1.0, meterRegistry.counter("notion_actions_executed_total").count());
    }

    // ==================== Test createPage() - Success ====================
    @Test
    void testCreatePageSuccess() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "parent-page-123",
            "title", "New Page Title"
        );

        Map<String, Object> response = Map.of(
            "id", "new-page-456",
            "url", "https://notion.so/new-page-456"
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/pages"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("new-page-456", result.get("page_id"));
        assertEquals("https://notion.so/new-page-456", result.get("url"));
    }

    // ==================== Test createPage() - With Content ====================
    @Test
    void testCreatePageWithContent() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "parent-page-123",
            "title", "New Page Title",
            "content", "This is the page content"
        );

        Map<String, Object> response = Map.of(
            "id", "new-page-456",
            "url", "https://notion.so/new-page-456"
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/pages"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("new-page-456", result.get("page_id"));

        verify(restTemplate).exchange(
            eq("https://api.notion.com/v1/pages"),
            eq(HttpMethod.POST),
            argThat(entity -> {
                HttpEntity<Map<String, Object>> httpEntity = (HttpEntity<Map<String, Object>>) entity;
                Map<String, Object> body = httpEntity.getBody();
                return body.containsKey("children");
            }),
            any(ParameterizedTypeReference.class)
        );
    }

    // ==================== Test updatePage() - Title Only ====================
    @Test
    void testUpdatePageTitleOnly() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "page-123",
            "title", "Updated Title"
        );

        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/pages/page-123"),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "update_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("page-123", result.get("page_id"));
        assertEquals("https://notion.so/page-123", result.get("url"));
    }

    // ==================== Test updatePage() - With Content ====================
    @Test
    void testUpdatePageWithContent() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "page-123",
            "title", "Updated Title",
            "content", "Updated content"
        );

        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "update_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("page-123", result.get("page_id"));

        // Verify both PATCH calls (page update and content update)
        verify(restTemplate, atLeast(2)).exchange(
            anyString(),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    // ==================== Test updatePage() - No Title No Content ====================
    @Test
    void testUpdatePageNoTitleNoContent() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of("page_id", "page-123");

        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/pages/page-123"),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "update_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("page-123", result.get("page_id"));
    }

    // ==================== Test createDatabaseItem() - Success ====================
    @Test
    void testCreateDatabaseItemSuccess() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "database_id", "db-123",
            "name", "New Item"
        );

        Map<String, Object> response = Map.of(
            "id", "item-456",
            "url", "https://notion.so/item-456"
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/pages"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_database_item", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("item-456", result.get("page_id"));
        assertEquals("https://notion.so/item-456", result.get("url"));
    }

    // ==================== Test updateDatabaseItem() - Success ====================
    @Test
    void testUpdateDatabaseItemSuccess() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "item-123",
            "name", "Updated Item Name"
        );

        Map<String, Object> response = Map.of(
            "id", "item-123",
            "url", "https://notion.so/item-123"
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/pages/item-123"),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "update_database_item", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("item-123", result.get("page_id"));
        assertEquals("https://notion.so/item-123", result.get("url"));
    }

    // ==================== Test archivePage() - Success ====================
    @Test
    void testArchivePageSuccess() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of("page_id", "page-123");

        Map<String, Object> response = Map.of(
            "id", "page-123",
            "archived", true
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/pages/page-123"),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "archive_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("page-123", result.get("page_id"));
        assertEquals(true, result.get("archived"));
    }

    // ==================== Test addComment() - Success ====================
    @Test
    void testAddCommentSuccess() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "page-123",
            "comment", "This is a comment"
        );

        Map<String, Object> response = Map.of(
            "id", "comment-789"
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/comments"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "add_comment", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("comment-789", result.get("comment_id"));
    }

    // ==================== Test createDatabase() - Success ====================
    @Test
    void testCreateDatabaseSuccess() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "parent-page-123",
            "title", "New Database"
        );

        Map<String, Object> response = Map.of(
            "id", "db-456",
            "url", "https://notion.so/db-456"
        );

        when(restTemplate.exchange(
            eq("https://api.notion.com/v1/databases"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_database", Map.of(), params, testUserId
        );

        assertNotNull(result);
        assertEquals("db-456", result.get("database_id"));
        assertEquals("https://notion.so/db-456", result.get("url"));
    }

    // ==================== Test getRequiredParam() - Success ====================
    @Test
    void testGetRequiredParamSuccess() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of("page_id", "page-123", "title", "Test");
        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
    }

    // ==================== Test getRequiredParam() - Missing Parameter ====================
    @Test
    void testGetRequiredParamMissingParameter() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of("title", "Test");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notionActionService.executeNotionAction("create_page", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("Required parameter missing"));
    }

    // ==================== Test getRequiredParam() - Wrong Type ====================
    @Test
    void testGetRequiredParamWrongType() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", 123, // Should be String
            "title", "Test"
        );

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notionActionService.executeNotionAction("create_page", Map.of(), params, testUserId);
        });

        assertTrue(exception.getMessage().contains("must be of type"));
    }

    // ==================== Test getOptionalParam() - Present ====================
    @Test
    void testGetOptionalParamPresent() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "page-123",
            "title", "Test",
            "content", "Optional content"
        );

        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
    }

    // ==================== Test getOptionalParam() - Not Present ====================
    @Test
    void testGetOptionalParamNotPresent() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "page-123",
            "title", "Test"
            // No content parameter
        );

        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
    }

    // ==================== Test getOptionalParam() - Wrong Type Returns Default ====================
    @Test
    void testGetOptionalParamWrongTypeReturnsDefault() {
        setupMockTokenFromServiceAccount();

        Map<String, Object> params = Map.of(
            "page_id", "page-123",
            "title", "Test",
            "content", 123 // Wrong type, should return default empty string
        );

        Map<String, Object> response = Map.of(
            "id", "page-123",
            "url", "https://notion.so/page-123"
        );

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        Map<String, Object> result = notionActionService.executeNotionAction(
            "create_page", Map.of(), params, testUserId
        );

        assertNotNull(result);
    }

    // ==================== Helper Methods ====================

    private void setupMockTokenFromServiceAccount() {
        when(serviceAccountService.getAccessToken(testUserId, "notion"))
            .thenReturn(Optional.of(testToken));
    }
}
