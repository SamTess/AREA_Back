package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.service.Area.ExecutionTriggerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotionWebhookServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ExecutionTriggerService executionTriggerService;

    @Mock
    private Counter webhookCounter;

    @Mock
    private Counter pageCreatedCounter;

    @Mock
    private Counter pageUpdatedCounter;

    @Mock
    private Counter webhookProcessingFailures;

    @InjectMocks
    private NotionWebhookService notionWebhookService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(webhookCounter);

        // Inject mock counters using ReflectionTestUtils
        ReflectionTestUtils.setField(notionWebhookService, "webhookCounter", webhookCounter);
        ReflectionTestUtils.setField(notionWebhookService, "pageCreatedCounter", pageCreatedCounter);
        ReflectionTestUtils.setField(notionWebhookService, "pageUpdatedCounter", pageUpdatedCounter);
        ReflectionTestUtils.setField(notionWebhookService, "webhookProcessingFailures", webhookProcessingFailures);
    }

    @Test
    void testInitMetrics() {
        // Test that initMetrics can be called without throwing an exception
        // This tests the @PostConstruct method
        assertDoesNotThrow(() -> notionWebhookService.initMetrics());
    }

    @Test
    void testProcessWebhook_PageCreated() {
        // Given
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", "Test Page Title");

        Map<String, Object> textObject = new HashMap<>();
        textObject.put("text", textContent);

        List<Map<String, Object>> titleArray = new ArrayList<>();
        titleArray.add(textObject);

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-123");
        page.put("title", titleArray);
        page.put("url", "https://notion.so/page-123");
        page.put("created_time", "2025-10-28T10:00:00.000Z");
        page.put("last_edited_time", "2025-10-28T10:00:00.000Z");

        Map<String, Object> createdBy = new HashMap<>();
        createdBy.put("id", "user-123");
        page.put("created_by", createdBy);

        Map<String, Object> parent = new HashMap<>();
        parent.put("type", "database_id");
        parent.put("database_id", "db-123");
        page.put("parent", parent);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("page.created", result.get("eventType"));
        assertEquals("page_created", result.get("action"));
        assertEquals("page-123", result.get("page_id"));
        assertEquals("Test Page Title", result.get("title"));
        verify(webhookCounter, times(1)).increment();
        verify(pageCreatedCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_PageCreated_WithProperties() {
        // Given - page with properties instead of direct title
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", "Property Title");

        Map<String, Object> textObject = new HashMap<>();
        textObject.put("text", textContent);

        List<Map<String, Object>> titleArray = new ArrayList<>();
        titleArray.add(textObject);

        Map<String, Object> titleProperty = new HashMap<>();
        titleProperty.put("type", "title");
        titleProperty.put("title", titleArray);

        Map<String, Object> properties = new HashMap<>();
        properties.put("Name", titleProperty);

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-456");
        page.put("properties", properties);
        page.put("url", "https://notion.so/page-456");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("page_created", result.get("action"));
        verify(pageCreatedCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_PageCreated_NoData() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("Missing page data", result.get("warning"));
        verify(pageCreatedCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_PageUpdated() {
        // Given
        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-789");

        Map<String, Object> data = new HashMap<>();
        data.put("property", "value");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.updated");
        payload.put("entity", page);
        payload.put("data", data);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("page.updated", result.get("eventType"));
        assertEquals("page_updated", result.get("action"));
        assertEquals("page-789", result.get("page_id"));
        verify(webhookCounter, times(1)).increment();
        verify(pageUpdatedCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_PageUpdated_DataFallback() {
        // Given - no entity, should fall back to data
        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-999");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.updated");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("page_updated", result.get("action"));
        assertEquals("page-999", result.get("page_id"));
        verify(pageUpdatedCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_PageContentUpdated() {
        // Given
        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-content-123");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.content_updated");
        payload.put("entity", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("page.content_updated", result.get("eventType"));
        assertEquals("page_updated", result.get("action"));
        verify(pageUpdatedCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_PageUpdated_NoData() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.updated");

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("Missing page data", result.get("warning"));
        verify(pageUpdatedCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_MissingEventType() {
        // Given
        Map<String, Object> payload = new HashMap<>();

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("Missing event type", result.get("warning"));
        verify(webhookCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_UnhandledEventType() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "unknown.event");

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("unknown.event", result.get("eventType"));
        assertTrue(result.get("info").toString().contains("Event type noted but no specific handler"));
        verify(webhookCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_WithSecret() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("secret", "test-secret");
        payload.put("signature", "test-signature");
        payload.put("token", "test-token");

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        verify(webhookCounter, times(1)).increment();
    }

    @Test
    void testProcessWebhook_Exception() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        
        // Force an exception by making data retrieval fail
        Map<String, Object> badData = new HashMap<String, Object>() {
            @Override
            public Object get(Object key) {
                if (key.equals("data")) {
                    throw new RuntimeException("Test exception");
                }
                return super.get(key);
            }
        };
        badData.put("type", "page.created");

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(badData);

        // Then
        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("error"));
        verify(webhookCounter, times(1)).increment();
        verify(webhookProcessingFailures, times(1)).increment();
    }

    @Test
    void testExtractPageEventData_Complete() {
        // Given
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", "Complete Page");

        Map<String, Object> textObject = new HashMap<>();
        textObject.put("text", textContent);

        List<Map<String, Object>> titleArray = new ArrayList<>();
        titleArray.add(textObject);

        Map<String, Object> createdBy = new HashMap<>();
        createdBy.put("id", "user-123");

        Map<String, Object> lastEditedBy = new HashMap<>();
        lastEditedBy.put("id", "user-456");

        Map<String, Object> parent = new HashMap<>();
        parent.put("type", "workspace");
        parent.put("workspace", "workspace-789");

        Map<String, Object> properties = new HashMap<>();
        properties.put("prop1", "value1");

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-complete");
        page.put("title", titleArray);
        page.put("url", "https://notion.so/page-complete");
        page.put("created_time", "2025-10-28T10:00:00.000Z");
        page.put("last_edited_time", "2025-10-28T11:00:00.000Z");
        page.put("created_by", createdBy);
        page.put("last_edited_by", lastEditedBy);
        page.put("parent", parent);
        page.put("properties", properties);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("page-complete", result.get("page_id"));
        assertEquals("Complete Page", result.get("title"));
    }

    @Test
    void testExtractNotionTitle_WithTitleArray() {
        // Given
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", "Title from array");

        Map<String, Object> textObject = new HashMap<>();
        textObject.put("text", textContent);

        List<Map<String, Object>> titleArray = new ArrayList<>();
        titleArray.add(textObject);

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-title-array");
        page.put("title", titleArray);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertEquals("Title from array", result.get("title"));
    }

    @Test
    void testExtractNotionTitle_WithProperties() {
        // Given
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", "Title from properties");

        Map<String, Object> textObject = new HashMap<>();
        textObject.put("text", textContent);

        List<Map<String, Object>> titleArray = new ArrayList<>();
        titleArray.add(textObject);

        Map<String, Object> titleProperty = new HashMap<>();
        titleProperty.put("type", "title");
        titleProperty.put("title", titleArray);

        Map<String, Object> properties = new HashMap<>();
        properties.put("Name", titleProperty);

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-title-props");
        page.put("properties", properties);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertEquals("Title from properties", result.get("title"));
    }

    @Test
    void testExtractNotionTitle_EmptyTitle() {
        // Given
        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-no-title");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertEquals("Untitled", result.get("title"));
    }

    @Test
    void testExtractNotionTitle_EmptyTitleArray() {
        // Given
        List<Map<String, Object>> emptyTitleArray = new ArrayList<>();

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-empty-title");
        page.put("title", emptyTitleArray);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertEquals("Untitled", result.get("title"));
    }

    @Test
    void testExtractNotionTitle_NullText() {
        // Given
        Map<String, Object> textObject = new HashMap<>();
        textObject.put("text", null);

        List<Map<String, Object>> titleArray = new ArrayList<>();
        titleArray.add(textObject);

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-null-text");
        page.put("title", titleArray);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertEquals("Untitled", result.get("title"));
    }

    @Test
    void testTriggerMatchingActions_Success() {
        // Given
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("new_page");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);
        actionInstance.setParams(new HashMap<>());

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-trigger");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(
            eq(actionInstance),
            eq(ActivationModeType.WEBHOOK),
            anyMap()
        );
    }

    @Test
    void testTriggerMatchingActions_DifferentActionKey() {
        // Given
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("different_action");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-no-match");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testTriggerMatchingActions_ExecutionException() {
        // Given
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("new_page");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);
        actionInstance.setParams(new HashMap<>());

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        doThrow(new RuntimeException("Execution failed"))
            .when(executionTriggerService).triggerAreaExecution(any(), any(), any());

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-exception");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testTriggerMatchingActions_RepositoryException() {
        // Given
        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenThrow(new RuntimeException("Database error"));

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-repo-error");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testShouldTriggerInstance_NoParams() {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setParams(null);

        // When - call processWebhook with matching action to trigger shouldTriggerInstance
        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("new_page");
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-no-params");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testShouldTriggerInstance_EmptyParams() {
        // Given
        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setParams(new HashMap<>());

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("new_page");
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-empty-params");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testShouldTriggerInstance_DatabaseIdMatch() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("database_id", "db-123");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setParams(params);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("new_page");
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> parent = new HashMap<>();
        parent.put("type", "database_id");
        parent.put("database_id", "db-123");

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-db-match");
        page.put("parent", parent);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testShouldTriggerInstance_DatabaseIdMismatch() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("database_id", "db-999");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setParams(params);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("new_page");
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> parent = new HashMap<>();
        parent.put("type", "database_id");
        parent.put("database_id", "db-123");

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-db-mismatch");
        page.put("parent", parent);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testShouldTriggerInstance_PageIdMatch() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("page_id", "page-123");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setParams(params);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("page_updated");
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-123");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.updated");
        payload.put("entity", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testShouldTriggerInstance_PageIdMismatch() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("page_id", "page-999");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setParams(params);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("page_updated");
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-123");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.updated");
        payload.put("entity", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, never()).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testShouldTriggerInstance_BothDatabaseAndPageId() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("database_id", "db-123");
        params.put("page_id", "page-456");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setParams(params);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("new_page");
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        Map<String, Object> parent = new HashMap<>();
        parent.put("type", "database_id");
        parent.put("database_id", "db-123");

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-456");
        page.put("parent", parent);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.created");
        payload.put("data", page);

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        verify(executionTriggerService, times(1)).triggerAreaExecution(any(), any(), any());
    }

    @Test
    void testProcessPageUpdatedEvent_WithAllFields() {
        // Given
        Map<String, Object> parent = new HashMap<>();
        parent.put("type", "database_id");
        parent.put("database_id", "db-789");

        Map<String, Object> page = new HashMap<>();
        page.put("id", "page-full-update");
        page.put("parent", parent);

        Map<String, Object> webhookData = new HashMap<>();
        webhookData.put("property", "updated_value");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "page.updated");
        payload.put("entity", page);
        payload.put("data", webhookData);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setKey("page_updated");

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setActionDefinition(actionDefinition);
        actionInstance.setEnabled(true);
        actionInstance.setParams(new HashMap<>());

        when(actionInstanceRepository.findEnabledActionInstancesByService("notion"))
            .thenReturn(Collections.singletonList(actionInstance));

        // When
        Map<String, Object> result = notionWebhookService.processWebhook(payload);

        // Then
        assertNotNull(result);
        assertEquals("page_updated", result.get("action"));
        assertEquals("page-full-update", result.get("page_id"));
        verify(executionTriggerService, times(1)).triggerAreaExecution(
            eq(actionInstance),
            eq(ActivationModeType.WEBHOOK),
            argThat(eventData -> {
                Map<String, Object> data = (Map<String, Object>) eventData;
                return "page-full-update".equals(data.get("page_id")) &&
                       "updated".equals(data.get("event_type")) &&
                       data.containsKey("webhook_data");
            })
        );
    }
}
