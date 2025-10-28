package area.server.AREA_Back.service.Webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PayloadMappingServiceTest {

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private PayloadMappingService payloadMappingService;

    private Map<String, Object> sourcePayload;

    @BeforeEach
    void setUp() {
        sourcePayload = new HashMap<>();
        sourcePayload.put("name", "John Doe");
        sourcePayload.put("age", 30);
        sourcePayload.put("email", "john@example.com");

        Map<String, Object> address = new HashMap<>();
        address.put("street", "123 Main St");
        address.put("city", "New York");
        sourcePayload.put("address", address);
    }

    @Test
    void testApplyMapping_WithValidMapping() {
        String mappingJson = """
            {
                "userName": "name",
                "userAge": "age",
                "userEmail": "email"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertEquals("John Doe", result.get("userName"));
        assertEquals(30, result.get("userAge"));
        assertEquals("john@example.com", result.get("userEmail"));
        assertEquals(3, result.size());
    }

    @Test
    void testApplyMapping_WithNestedFields() {
        String mappingJson = """
            {
                "city": "address.city",
                "street": "address.street"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertEquals("New York", result.get("city"));
        assertEquals("123 Main St", result.get("street"));
    }

    @Test
    void testApplyMapping_WithNullMapping() {
        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, null);

        assertNotNull(result);
        assertEquals(sourcePayload, result);
    }

    @Test
    void testApplyMapping_WithEmptyMapping() {
        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, "");

        assertNotNull(result);
        assertEquals(sourcePayload, result);
    }

    @Test
    void testApplyMapping_WithWhitespaceMapping() {
        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, "   ");

        assertNotNull(result);
        assertEquals(sourcePayload, result);
    }

    @Test
    void testApplyMapping_WithNonExistentField() {
        String mappingJson = """
            {
                "nonExistent": "doesNotExist",
                "userName": "name"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertEquals("John Doe", result.get("userName"));
        assertNull(result.get("nonExistent"));
        assertEquals(1, result.size());
    }

    @Test
    void testApplyMapping_WithInvalidJson() {
        String invalidJson = "{ invalid json }";

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, invalidJson);

        assertNotNull(result);
        assertEquals(sourcePayload, result);
    }

    @Test
    void testApplyMappingWithMerge_Success() {
        String mappingJson = """
            {
                "userName": "name"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMappingWithMerge(sourcePayload, mappingJson);

        assertNotNull(result);
        assertTrue(result.containsKey("userName"));
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("age"));
        assertTrue(result.containsKey("email"));
        assertEquals("John Doe", result.get("userName"));
        assertEquals("John Doe", result.get("name"));
    }

    @Test
    void testApplyMappingWithMerge_EmptyMapping() {
        Map<String, Object> result = payloadMappingService.applyMappingWithMerge(sourcePayload, null);

        assertNotNull(result);
        assertEquals(sourcePayload, result);
    }

    @Test
    void testApplyMappingWithMerge_InvalidMapping() {
        String invalidJson = "invalid";

        Map<String, Object> result = payloadMappingService.applyMappingWithMerge(sourcePayload, invalidJson);

        assertNotNull(result);
        assertEquals(sourcePayload, result);
    }

    @Test
    void testExtractValue_WithDeepNesting() {
        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        Map<String, Object> level3 = new HashMap<>();
        level3.put("value", "deepValue");
        level2.put("level3", level3);
        level1.put("level2", level2);
        sourcePayload.put("level1", level1);

        String mappingJson = """
            {
                "deep": "level1.level2.level3.value"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertEquals("deepValue", result.get("deep"));
    }

    @Test
    void testExtractValue_WithNullIntermediateValue() {
        sourcePayload.put("nullField", null);

        String mappingJson = """
            {
                "result": "nullField.subField"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertNull(result.get("result"));
    }

    @Test
    void testCreateGitHubIssueToCommentMapping() {
        String mapping = payloadMappingService.createGitHubIssueToCommentMapping();

        assertNotNull(mapping);
        assertTrue(mapping.contains("issue_number"));
        assertTrue(mapping.contains("repository"));
        assertTrue(mapping.contains("issue_title"));
        assertTrue(mapping.contains("issue_url"));
    }

    @Test
    void testCreateGitHubPRMapping() {
        String mapping = payloadMappingService.createGitHubPRMapping();

        assertNotNull(mapping);
        assertTrue(mapping.contains("pr_number"));
        assertTrue(mapping.contains("repository"));
        assertTrue(mapping.contains("pr_title"));
        assertTrue(mapping.contains("pr_url"));
        assertTrue(mapping.contains("pr_state"));
    }

    @Test
    void testApplyMapping_WithComplexGitHubIssuePayload() {
        Map<String, Object> issue = new HashMap<>();
        issue.put("number", 123);
        issue.put("title", "Test Issue");
        issue.put("html_url", "https://github.com/test/repo/issues/123");

        Map<String, Object> repository = new HashMap<>();
        repository.put("full_name", "test/repo");

        Map<String, Object> githubPayload = new HashMap<>();
        githubPayload.put("issue", issue);
        githubPayload.put("repository", repository);

        String mapping = payloadMappingService.createGitHubIssueToCommentMapping();
        Map<String, Object> result = payloadMappingService.applyMapping(githubPayload, mapping);

        assertNotNull(result);
        assertEquals(123, result.get("issue_number"));
        assertEquals("test/repo", result.get("repository"));
        assertEquals("Test Issue", result.get("issue_title"));
        assertEquals("https://github.com/test/repo/issues/123", result.get("issue_url"));
    }

    @Test
    void testApplyMapping_WithComplexGitHubPRPayload() {
        Map<String, Object> pullRequest = new HashMap<>();
        pullRequest.put("number", 456);
        pullRequest.put("title", "Test PR");
        pullRequest.put("html_url", "https://github.com/test/repo/pull/456");
        pullRequest.put("state", "open");

        Map<String, Object> repository = new HashMap<>();
        repository.put("full_name", "test/repo");

        Map<String, Object> githubPayload = new HashMap<>();
        githubPayload.put("pull_request", pullRequest);
        githubPayload.put("repository", repository);

        String mapping = payloadMappingService.createGitHubPRMapping();
        Map<String, Object> result = payloadMappingService.applyMapping(githubPayload, mapping);

        assertNotNull(result);
        assertEquals(456, result.get("pr_number"));
        assertEquals("test/repo", result.get("repository"));
        assertEquals("Test PR", result.get("pr_title"));
        assertEquals("https://github.com/test/repo/pull/456", result.get("pr_url"));
        assertEquals("open", result.get("pr_state"));
    }

    @Test
    void testApplyMapping_WithEmptySourcePayload() {
        String mappingJson = """
            {
                "userName": "name"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(new HashMap<>(), mappingJson);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testApplyMapping_WithArrayValues() {
        String[] tags = {"tag1", "tag2", "tag3"};
        sourcePayload.put("tags", tags);

        String mappingJson = """
            {
                "userTags": "tags"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertArrayEquals(tags, (String[]) result.get("userTags"));
    }

    @Test
    void testApplyMapping_WithNullValue() {
        sourcePayload.put("nullField", null);

        String mappingJson = """
            {
                "mappedNull": "nullField"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractValue_WithEmptyPath() {
        String mappingJson = """
            {
                "result": ""
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertFalse(result.containsKey("result"));
    }

    @Test
    void testApplyMapping_WithSpecialCharacters() {
        sourcePayload.put("special-field", "special value");
        sourcePayload.put("field.with.dots", "dotted value");

        String mappingJson = """
            {
                "specialMapped": "special-field"
            }
            """;

        Map<String, Object> result = payloadMappingService.applyMapping(sourcePayload, mappingJson);

        assertNotNull(result);
        assertEquals("special value", result.get("specialMapped"));
    }
}
