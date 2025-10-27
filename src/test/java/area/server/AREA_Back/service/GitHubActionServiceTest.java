package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Area.Services.GitHubActionService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubActionServiceTest {

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
    private GitHubActionService gitHubActionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        gitHubActionService = new GitHubActionService(
            userOAuthIdentityRepository,
            userRepository,
            tokenEncryptionService,
            serviceAccountService,
            restTemplate,
            meterRegistry
        );
        // Manually initialize metrics since @PostConstruct won't run in tests
        gitHubActionService.init();
    }

    @Test
    void testServiceInitialization() {
        // Test that the service is properly initialized with metrics
        assertNotNull(gitHubActionService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testExecuteGitHubActionWithInvalidActionKey() {
        // Given
        String invalidActionKey = "invalid_action";
        Map<String, Object> inputPayload = Map.of();
        Map<String, Object> actionParams = Map.of();
        UUID userId = UUID.randomUUID();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            gitHubActionService.executeGitHubAction(invalidActionKey, inputPayload, actionParams, userId);
        });
        assertTrue(exception.getMessage().contains("No GitHub token found"));
    }

    @Test
    void testExecuteGitHubActionWithNullToken() {
        // Given
        String actionKey = "create_issue";
        Map<String, Object> inputPayload = Map.of();
        Map<String, Object> actionParams = Map.of(
            "repository", "owner/repo",
            "title", "Test Issue",
            "body", "Test body"
        );
        UUID userId = UUID.randomUUID();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            gitHubActionService.executeGitHubAction(actionKey, inputPayload, actionParams, userId);
        });
        assertTrue(exception.getMessage().contains("GitHub action execution failed"));
    }

    @Test
    void testCheckGitHubEventsWithInvalidActionKey() {
        // Given
        String invalidActionKey = "invalid_event";
        Map<String, Object> actionParams = Map.of();
        UUID userId = UUID.randomUUID();

        // When
        var result = gitHubActionService.checkGitHubEvents(invalidActionKey, actionParams, userId, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckGitHubEventsWithNullToken() {
        // Given
        String actionKey = "new_issue";
        Map<String, Object> actionParams = Map.of("repository", "owner/repo");
        UUID userId = UUID.randomUUID();

        // When
        var result = gitHubActionService.checkGitHubEvents(actionKey, actionParams, userId, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCreateIssue() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("title", "Test Issue");
        actionParams.put("body", "Test body");
        actionParams.put("labels", List.of("bug", "enhancement"));
        actionParams.put("assignees", List.of("user1", "user2"));

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("number", 123);
        responseBody.put("html_url", "https://github.com/owner/repo/issues/123");
        responseBody.put("created_at", "2025-10-27T12:00:00Z");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.CREATED));

        // When
        Map<String, Object> result = gitHubActionService.executeGitHubAction(
            "create_issue", inputPayload, actionParams, userId);

        // Then
        assertNotNull(result);
        assertEquals(123, result.get("issue_number"));
        assertEquals("https://github.com/owner/repo/issues/123", result.get("html_url"));
        assertEquals("2025-10-27T12:00:00Z", result.get("created_at"));
    }

    @Test
    void testCreateIssueWithoutOptionalParams() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("title", "Test Issue");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("number", 456);
        responseBody.put("html_url", "https://github.com/owner/repo/issues/456");
        responseBody.put("created_at", "2025-10-27T12:00:00Z");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.CREATED));

        // When
        Map<String, Object> result = gitHubActionService.executeGitHubAction(
            "create_issue", inputPayload, actionParams, userId);

        // Then
        assertNotNull(result);
        assertEquals(456, result.get("issue_number"));
    }

    @Test
    void testCreateIssueMissingRequiredParam() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        // Missing "title" parameter

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            gitHubActionService.executeGitHubAction("create_issue", inputPayload, actionParams, userId);
        });
        assertTrue(exception.getMessage().contains("GitHub action execution failed"));
    }

    @Test
    void testCommentIssue() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("issue_number", 123);
        actionParams.put("comment", "This is a test comment");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", 789);
        responseBody.put("html_url", "https://github.com/owner/repo/issues/123#issuecomment-789");
        responseBody.put("created_at", "2025-10-27T12:30:00Z");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.CREATED));

        // When
        Map<String, Object> result = gitHubActionService.executeGitHubAction(
            "comment_issue", inputPayload, actionParams, userId);

        // Then
        assertNotNull(result);
        assertEquals(789, result.get("comment_id"));
        assertEquals("https://github.com/owner/repo/issues/123#issuecomment-789", result.get("html_url"));
        assertEquals("2025-10-27T12:30:00Z", result.get("created_at"));
    }

    @Test
    void testCloseIssue() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("issue_number", 123);

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("number", 123);
        responseBody.put("state", "closed");
        responseBody.put("closed_at", "2025-10-27T13:00:00Z");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = gitHubActionService.executeGitHubAction(
            "close_issue", inputPayload, actionParams, userId);

        // Then
        assertNotNull(result);
        assertEquals(123, result.get("issue_number"));
        assertEquals("closed", result.get("state"));
        assertEquals("2025-10-27T13:00:00Z", result.get("closed_at"));
    }

    @Test
    void testCloseIssueWithComment() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("issue_number", 123);
        actionParams.put("comment", "Closing this issue");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        Map<String, Object> commentResponse = new HashMap<>();
        commentResponse.put("id", 999);
        commentResponse.put("html_url", "https://github.com/owner/repo/issues/123#issuecomment-999");
        commentResponse.put("created_at", "2025-10-27T13:00:00Z");

        Map<String, Object> closeResponse = new HashMap<>();
        closeResponse.put("number", 123);
        closeResponse.put("state", "closed");
        closeResponse.put("closed_at", "2025-10-27T13:00:00Z");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(commentResponse, HttpStatus.CREATED));
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(closeResponse, HttpStatus.OK));

        // When
        Map<String, Object> result = gitHubActionService.executeGitHubAction(
            "close_issue", inputPayload, actionParams, userId);

        // Then
        assertNotNull(result);
        assertEquals(123, result.get("issue_number"));
        assertEquals("closed", result.get("state"));
    }

    @Test
    void testAddLabel() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("issue_number", 123);
        actionParams.put("labels", List.of("bug", "priority-high"));

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        List<Map<String, Object>> responseBody = new ArrayList<>();
        Map<String, Object> label1 = new HashMap<>();
        label1.put("name", "bug");
        Map<String, Object> label2 = new HashMap<>();
        label2.put("name", "priority-high");
        responseBody.add(label1);
        responseBody.add(label2);

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = gitHubActionService.executeGitHubAction(
            "add_label", inputPayload, actionParams, userId);

        // Then
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) result.get("labels");
        assertEquals(2, labels.size());
        assertTrue(labels.contains("bug"));
        assertTrue(labels.contains("priority-high"));
    }

    @Test
    void testCheckNewIssues() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        LocalDateTime lastCheck = LocalDateTime.parse("2025-10-27T10:00:00");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        List<Map<String, Object>> issuesResponse = new ArrayList<>();
        Map<String, Object> issue1 = new HashMap<>();
        issue1.put("number", 123);
        issue1.put("title", "Test Issue");
        issue1.put("body", "Test body");
        issue1.put("created_at", "2025-10-27T11:00:00Z");
        issue1.put("html_url", "https://github.com/owner/repo/issues/123");
        Map<String, Object> user1 = new HashMap<>();
        user1.put("login", "testuser");
        issue1.put("user", user1);
        List<Map<String, Object>> labels1 = new ArrayList<>();
        Map<String, Object> label1 = new HashMap<>();
        label1.put("name", "bug");
        labels1.add(label1);
        issue1.put("labels", labels1);
        issuesResponse.add(issue1);

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(issuesResponse, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_issue", actionParams, userId, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(123, result.get(0).get("issue_number"));
        assertEquals("Test Issue", result.get(0).get("title"));
        assertEquals("testuser", result.get(0).get("author"));
    }

    @Test
    void testCheckNewIssuesWithLabelFilter() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("labels", List.of("bug"));
        LocalDateTime lastCheck = LocalDateTime.parse("2025-10-27T10:00:00");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        List<Map<String, Object>> issuesResponse = new ArrayList<>();
        
        Map<String, Object> issue1 = new HashMap<>();
        issue1.put("number", 123);
        issue1.put("title", "Bug Issue");
        issue1.put("body", "Test body");
        issue1.put("created_at", "2025-10-27T11:00:00Z");
        issue1.put("html_url", "https://github.com/owner/repo/issues/123");
        Map<String, Object> user1 = new HashMap<>();
        user1.put("login", "testuser");
        issue1.put("user", user1);
        List<Map<String, Object>> labels1 = new ArrayList<>();
        Map<String, Object> bugLabel = new HashMap<>();
        bugLabel.put("name", "bug");
        labels1.add(bugLabel);
        issue1.put("labels", labels1);
        issuesResponse.add(issue1);

        Map<String, Object> issue2 = new HashMap<>();
        issue2.put("number", 124);
        issue2.put("title", "Feature Request");
        issue2.put("body", "Test body");
        issue2.put("created_at", "2025-10-27T11:30:00Z");
        issue2.put("html_url", "https://github.com/owner/repo/issues/124");
        issue2.put("user", user1);
        List<Map<String, Object>> labels2 = new ArrayList<>();
        Map<String, Object> featureLabel = new HashMap<>();
        featureLabel.put("name", "enhancement");
        labels2.add(featureLabel);
        issue2.put("labels", labels2);
        issuesResponse.add(issue2);

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(issuesResponse, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_issue", actionParams, userId, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(123, result.get(0).get("issue_number"));
        assertEquals("Bug Issue", result.get(0).get("title"));
    }

    @Test
    void testCheckNewPullRequests() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("target_branch", "main");
        LocalDateTime lastCheck = LocalDateTime.parse("2025-10-27T10:00:00");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        List<Map<String, Object>> prsResponse = new ArrayList<>();
        Map<String, Object> pr1 = new HashMap<>();
        pr1.put("number", 456);
        pr1.put("title", "Test PR");
        pr1.put("body", "PR body");
        pr1.put("created_at", "2025-10-27T11:00:00Z");
        pr1.put("html_url", "https://github.com/owner/repo/pull/456");
        Map<String, Object> user1 = new HashMap<>();
        user1.put("login", "prauthor");
        pr1.put("user", user1);
        Map<String, Object> head = new HashMap<>();
        head.put("ref", "feature-branch");
        pr1.put("head", head);
        Map<String, Object> base = new HashMap<>();
        base.put("ref", "main");
        pr1.put("base", base);
        prsResponse.add(pr1);

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(prsResponse, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_pull_request", actionParams, userId, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(456, result.get(0).get("pr_number"));
        assertEquals("Test PR", result.get(0).get("title"));
        assertEquals("prauthor", result.get(0).get("author"));
        assertEquals("feature-branch", result.get(0).get("source_branch"));
        assertEquals("main", result.get(0).get("target_branch"));
    }

    @Test
    void testCheckPushToBranch() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("branch", "main");
        LocalDateTime lastCheck = LocalDateTime.parse("2025-10-27T10:00:00");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        List<Map<String, Object>> commitsResponse = new ArrayList<>();
        Map<String, Object> commit1 = new HashMap<>();
        commit1.put("sha", "abc123");
        commit1.put("html_url", "https://github.com/owner/repo/commit/abc123");
        Map<String, Object> commitData = new HashMap<>();
        commitData.put("message", "Test commit message");
        Map<String, Object> author = new HashMap<>();
        author.put("name", "John Doe");
        author.put("date", "2025-10-27T11:00:00Z");
        commitData.put("author", author);
        commit1.put("commit", commitData);
        commitsResponse.add(commit1);

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(commitsResponse, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "push_to_branch", actionParams, userId, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("abc123", result.get(0).get("commit_sha"));
        assertEquals("Test commit message", result.get(0).get("commit_message"));
        assertEquals("John Doe", result.get(0).get("author"));
        assertEquals("main", result.get(0).get("branch"));
    }

    @Test
    void testGetGitHubTokenFromServiceAccount() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "service-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("title", "Test Issue");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("number", 789);
        responseBody.put("html_url", "https://github.com/owner/repo/issues/789");
        responseBody.put("created_at", "2025-10-27T12:00:00Z");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.of(token));
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.CREATED));

        // When
        Map<String, Object> result = gitHubActionService.executeGitHubAction(
            "create_issue", Map.of(), actionParams, userId);

        // Then
        assertNotNull(result);
        assertEquals(789, result.get("issue_number"));
    }

    @Test
    void testGetGitHubTokenUserNotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_issue", actionParams, userId, LocalDateTime.now());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetGitHubTokenOAuthNotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.empty());

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_issue", actionParams, userId, LocalDateTime.now());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetGitHubTokenEmptyToken() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("");
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_issue", actionParams, userId, LocalDateTime.now());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetGitHubTokenDecryptionError() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenThrow(new RuntimeException("Decryption failed"));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_issue", actionParams, userId, LocalDateTime.now());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetRequiredParamMissing() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        // Missing issue_number

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            gitHubActionService.executeGitHubAction("comment_issue", Map.of(), actionParams, userId);
        });
        assertTrue(exception.getMessage().contains("GitHub action execution failed"));
    }

    @Test
    void testGetRequiredParamWrongType() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("issue_number", "not-a-number");
        actionParams.put("comment", "Test comment");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            gitHubActionService.executeGitHubAction("comment_issue", Map.of(), actionParams, userId);
        });
        assertTrue(exception.getMessage().contains("GitHub action execution failed"));
    }

    @Test
    void testCheckNewIssuesBeforeLastCheck() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        LocalDateTime lastCheck = LocalDateTime.parse("2025-10-27T12:00:00");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        List<Map<String, Object>> issuesResponse = new ArrayList<>();
        Map<String, Object> issue1 = new HashMap<>();
        issue1.put("number", 123);
        issue1.put("title", "Old Issue");
        issue1.put("body", "Test body");
        issue1.put("created_at", "2025-10-27T10:00:00Z");
        issue1.put("html_url", "https://github.com/owner/repo/issues/123");
        Map<String, Object> user1 = new HashMap<>();
        user1.put("login", "testuser");
        issue1.put("user", user1);
        issue1.put("labels", Collections.emptyList());
        issuesResponse.add(issue1);

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(issuesResponse, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_issue", actionParams, userId, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewPullRequestsBeforeLastCheck() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        LocalDateTime lastCheck = LocalDateTime.parse("2025-10-27T12:00:00");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        List<Map<String, Object>> prsResponse = new ArrayList<>();
        Map<String, Object> pr1 = new HashMap<>();
        pr1.put("number", 456);
        pr1.put("created_at", "2025-10-27T10:00:00Z");
        prsResponse.add(pr1);

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(prsResponse, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "new_pull_request", actionParams, userId, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckPushToBranchBeforeLastCheck() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = "test-token";
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("repository", "owner/repo");
        actionParams.put("branch", "main");
        LocalDateTime lastCheck = LocalDateTime.parse("2025-10-27T12:00:00");

        User user = new User();
        user.setId(userId);
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setAccessTokenEnc("encrypted-token");

        List<Map<String, Object>> commitsResponse = new ArrayList<>();
        Map<String, Object> commit1 = new HashMap<>();
        commit1.put("sha", "abc123");
        Map<String, Object> commitData = new HashMap<>();
        Map<String, Object> author = new HashMap<>();
        author.put("date", "2025-10-27T10:00:00Z");
        commitData.put("author", author);
        commit1.put("commit", commitData);
        commitsResponse.add(commit1);

        when(serviceAccountService.getAccessToken(userId, "github")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(oauth));
        when(tokenEncryptionService.decryptToken("encrypted-token")).thenReturn(token);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(commitsResponse, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = gitHubActionService.checkGitHubEvents(
            "push_to_branch", actionParams, userId, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}