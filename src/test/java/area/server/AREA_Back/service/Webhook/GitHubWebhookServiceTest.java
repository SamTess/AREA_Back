package area.server.AREA_Back.service.Webhook;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitHubWebhookServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter webhookCounter;

    @Mock
    private Counter issuesEventCounter;

    @Mock
    private Counter pullRequestEventCounter;

    @Mock
    private Counter pushEventCounter;

    @Mock
    private Counter pingEventCounter;

    @Mock
    private Counter webhookProcessingFailures;

    @InjectMocks
    private GitHubWebhookService gitHubWebhookService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        lenient().when(meterRegistry.counter(anyString())).thenReturn(webhookCounter);
        
        // Inject mock counters directly using ReflectionTestUtils
        ReflectionTestUtils.setField(gitHubWebhookService, "webhookCounter", webhookCounter);
        ReflectionTestUtils.setField(gitHubWebhookService, "issuesEventCounter", issuesEventCounter);
        ReflectionTestUtils.setField(gitHubWebhookService, "pullRequestEventCounter", pullRequestEventCounter);
        ReflectionTestUtils.setField(gitHubWebhookService, "pushEventCounter", pushEventCounter);
        ReflectionTestUtils.setField(gitHubWebhookService, "pingEventCounter", pingEventCounter);
        ReflectionTestUtils.setField(gitHubWebhookService, "webhookProcessingFailures", webhookProcessingFailures);
    }

    @Test
    void testProcessWebhook_IssuesEvent() {
        Map<String, Object> issue = new HashMap<>();
        issue.put("number", 123);
        issue.put("title", "Test Issue");

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");
        payload.put("issue", issue);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "issues", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals(userId.toString(), result.get("userId"));
        assertEquals("issues", result.get("eventType"));
        assertNotNull(result.get("processedAt"));
        verify(webhookCounter, atLeastOnce()).increment();
    }

    @Test
    void testProcessWebhook_PullRequestEvent() {
        Map<String, Object> pullRequest = new HashMap<>();
        pullRequest.put("number", 456);
        pullRequest.put("title", "Test PR");

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");
        payload.put("pull_request", pullRequest);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "pull_request", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("pull_request", result.get("eventType"));
    }

    @Test
    void testProcessWebhook_PushEvent() {
        List<Map<String, Object>> commits = new ArrayList<>();
        Map<String, Object> commit1 = new HashMap<>();
        commit1.put("id", "abc123");
        commits.add(commit1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ref", "refs/heads/main");
        payload.put("commits", commits);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "push", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("push", result.get("eventType"));
    }

    @Test
    void testProcessWebhook_PingEvent() {
        Map<String, Object> hook = new HashMap<>();
        hook.put("id", 12345);

        Map<String, Object> payload = new HashMap<>();
        payload.put("hook", hook);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "ping", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
        assertEquals("ping", result.get("eventType"));
    }

    @Test
    void testProcessWebhook_UnsupportedEvent() {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "unsupported_event", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessIssuesEvent_WithAction() {
        Map<String, Object> issue = new HashMap<>();
        issue.put("number", 789);
        issue.put("title", "Bug Report");
        issue.put("state", "open");

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "closed");
        payload.put("issue", issue);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "issues", "sha256=test", payload
        );

        assertNotNull(result);
        assertTrue(result.containsKey("processedAt"));
    }

    @Test
    void testProcessIssuesEvent_NullIssue() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");
        payload.put("issue", null);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "issues", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessPullRequestEvent_WithAction() {
        Map<String, Object> pullRequest = new HashMap<>();
        pullRequest.put("number", 999);
        pullRequest.put("title", "Feature Addition");
        pullRequest.put("state", "open");

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "synchronize");
        payload.put("pull_request", pullRequest);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "pull_request", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessPullRequestEvent_NullPullRequest() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");
        payload.put("pull_request", null);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "pull_request", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessPushEvent_MultipleCommits() {
        List<Map<String, Object>> commits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> commit = new HashMap<>();
            commit.put("id", "commit" + i);
            commits.add(commit);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("ref", "refs/heads/develop");
        payload.put("commits", commits);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "push", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessPushEvent_NoCommits() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ref", "refs/heads/main");
        payload.put("commits", null);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "push", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessPushEvent_EmptyCommits() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ref", "refs/heads/main");
        payload.put("commits", new ArrayList<>());

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "push", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessPingEvent_WithHook() {
        Map<String, Object> hook = new HashMap<>();
        hook.put("id", 54321);
        hook.put("type", "Repository");

        Map<String, Object> payload = new HashMap<>();
        payload.put("hook", hook);
        payload.put("zen", "Design for failure.");

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "ping", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessPingEvent_NullHook() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hook", null);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "ping", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_WithException() {
        // This test verifies error handling
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "issues", "sha256=test", payload
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhook_NullSignature() {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "ping", null, payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_EmptyPayload() {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "issues", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testInit() {
        when(meterRegistry.counter(anyString())).thenReturn(webhookCounter);

        assertDoesNotThrow(() -> gitHubWebhookService.init());
        verify(meterRegistry, atLeastOnce()).counter("webhook.counter");
    }

    @Test
    void testProcessWebhook_DifferentEventTypes() {
        String[] eventTypes = {"release", "fork", "star", "watch", "create", "delete"};
        
        for (String eventType : eventTypes) {
            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> result = gitHubWebhookService.processWebhook(
                userId, eventType, "sha256=test", payload
            );

            assertNotNull(result);
            assertEquals("processed", result.get("status"));
        }
    }

    @Test
    void testProcessWebhook_ComplexIssuePayload() {
        Map<String, Object> user = new HashMap<>();
        user.put("login", "testuser");

        Map<String, Object> issue = new HashMap<>();
        issue.put("number", 1);
        issue.put("title", "Complex Issue");
        issue.put("body", "This is a complex issue");
        issue.put("user", user);
        issue.put("labels", Arrays.asList("bug", "priority"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "labeled");
        payload.put("issue", issue);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "issues", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_ComplexPRPayload() {
        Map<String, Object> head = new HashMap<>();
        head.put("ref", "feature-branch");

        Map<String, Object> base = new HashMap<>();
        base.put("ref", "main");

        Map<String, Object> pullRequest = new HashMap<>();
        pullRequest.put("number", 42);
        pullRequest.put("title", "Complex PR");
        pullRequest.put("head", head);
        pullRequest.put("base", base);
        pullRequest.put("merged", false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "review_requested");
        payload.put("pull_request", pullRequest);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "pull_request", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_NullEventType() {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, null, "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }

    @Test
    void testProcessWebhook_WithRepositoryInfo() {
        Map<String, Object> repository = new HashMap<>();
        repository.put("name", "test-repo");
        repository.put("full_name", "user/test-repo");

        Map<String, Object> payload = new HashMap<>();
        payload.put("repository", repository);

        Map<String, Object> result = gitHubWebhookService.processWebhook(
            userId, "ping", "sha256=test", payload
        );

        assertNotNull(result);
        assertEquals("processed", result.get("status"));
    }
}
