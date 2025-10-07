package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubActionService {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final int DATETIME_PREFIX_LENGTH = 19;
    private static final String GITHUB_PROVIDER_KEY = "github";

    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate = new RestTemplate();

    private final MeterRegistry meterRegistry;
    private Counter githubActionsExecuted;
    private Counter githubActionsFailed;

    @PostConstruct
    public void init() {
        githubActionsExecuted = meterRegistry.counter("github_actions_executed_total");
        githubActionsFailed = meterRegistry.counter("github_actions_failed_total");
    }

    public Map<String, Object> executeGitHubAction(String actionKey,
                                                   Map<String, Object> inputPayload,
                                                   Map<String, Object> actionParams,
                                                   UUID userId) {
        try {
            githubActionsExecuted.increment();

            String githubToken = getGitHubToken(userId);
            if (githubToken == null) {
                throw new RuntimeException("No GitHub token found for user: " + userId);
            }

            switch (actionKey) {
                case "create_issue":
                    return createIssue(githubToken, inputPayload, actionParams);
                case "comment_issue":
                    return commentIssue(githubToken, inputPayload, actionParams);
                case "close_issue":
                    return closeIssue(githubToken, inputPayload, actionParams);
                case "add_label":
                    return addLabel(githubToken, inputPayload, actionParams);
                default:
                    throw new IllegalArgumentException("Unknown GitHub action: " + actionKey);
            }
        } catch (Exception e) {
            githubActionsFailed.increment();
            log.error("Failed to execute GitHub action { }: { }", actionKey, e.getMessage(), e);
            throw new RuntimeException("GitHub action execution failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> checkGitHubEvents(String actionKey,
                                                        Map<String, Object> actionParams,
                                                        UUID userId,
                                                        LocalDateTime lastCheck) {
        try {
            log.debug("Checking GitHub events: { } for user: { }", actionKey, userId);

            String githubToken = getGitHubToken(userId);
            if (githubToken == null) {
                log.warn("No GitHub token found for user: { }", userId);
                return Collections.emptyList();
            }

            switch (actionKey) {
                case "new_issue":
                    return checkNewIssues(githubToken, actionParams, lastCheck);
                case "new_pull_request":
                    return checkNewPullRequests(githubToken, actionParams, lastCheck);
                case "push_to_branch":
                    return checkPushToBranch(githubToken, actionParams, lastCheck);
                default:
                    log.warn("Unknown GitHub event action: { }", actionKey);
                    return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to check GitHub events { }: { }", actionKey, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> createIssue(String token, Map<String, Object> input, Map<String, Object> params) {
        String repository = getRequiredParam(params, "repository", String.class);
        String title = getRequiredParam(params, "title", String.class);
        String body = getOptionalParam(params, "body", String.class, "");

        @SuppressWarnings("unchecked")
        List<String> labels = getOptionalParam(params, "labels", List.class, Collections.emptyList());

        @SuppressWarnings("unchecked")
        List<String> assignees = getOptionalParam(params, "assignees", List.class, Collections.emptyList());

        String url = String.format("%s/repos/%s/issues", GITHUB_API_BASE, repository);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("title", title);
        requestBody.put("body", body);
        if (!labels.isEmpty()) {
            requestBody.put("labels", labels);
        }
        if (!assignees.isEmpty()) {
            requestBody.put("assignees", assignees);
        }

        HttpHeaders headers = createGitHubHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create GitHub issue: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("issue_number", responseBody.get("number"));
        result.put("html_url", responseBody.get("html_url"));
        result.put("created_at", responseBody.get("created_at"));

        return result;
    }

    private Map<String, Object> commentIssue(String token, Map<String, Object> input, Map<String, Object> params) {
        String repository = getRequiredParam(params, "repository", String.class);
        Integer issueNumber = getRequiredParam(params, "issue_number", Integer.class);
        String comment = getRequiredParam(params, "comment", String.class);

        String url = String.format("%s/repos/%s/issues/%d/comments", GITHUB_API_BASE, repository, issueNumber);

        Map<String, Object> requestBody = Map.of("body", comment);
        HttpHeaders headers = createGitHubHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to comment on GitHub issue: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("comment_id", responseBody.get("id"));
        result.put("html_url", responseBody.get("html_url"));
        result.put("created_at", responseBody.get("created_at"));

        return result;
    }

    private Map<String, Object> closeIssue(String token, Map<String, Object> input, Map<String, Object> params) {
        String repository = getRequiredParam(params, "repository", String.class);
        Integer issueNumber = getRequiredParam(params, "issue_number", Integer.class);
        String comment = getOptionalParam(params, "comment", String.class, null);

        if (comment != null && !comment.trim().isEmpty()) {
            Map<String, Object> commentParams = new HashMap<>();
            commentParams.put("repository", repository);
            commentParams.put("issue_number", issueNumber);
            commentParams.put("comment", comment);
            commentIssue(token, input, commentParams);
        }

        String url = String.format("%s/repos/%s/issues/%d", GITHUB_API_BASE, repository, issueNumber);

        Map<String, Object> requestBody = Map.of("state", "closed");
        HttpHeaders headers = createGitHubHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.PATCH, request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to close GitHub issue: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("issue_number", responseBody.get("number"));
        result.put("state", responseBody.get("state"));
        result.put("closed_at", responseBody.get("closed_at"));

        return result;
    }

    private Map<String, Object> addLabel(String token, Map<String, Object> input, Map<String, Object> params) {
        String repository = getRequiredParam(params, "repository", String.class);
        Integer issueNumber = getRequiredParam(params, "issue_number", Integer.class);

        @SuppressWarnings("unchecked")
        List<String> labels = getRequiredParam(params, "labels", List.class);

        String url = String.format("%s/repos/%s/issues/%d/labels", GITHUB_API_BASE, repository, issueNumber);

        Map<String, Object> requestBody = Map.of("labels", labels);
        HttpHeaders headers = createGitHubHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<List<Map<String, Object>>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to add labels to GitHub issue: " + response.getStatusCode());
        }

        List<Map<String, Object>> responseBody = response.getBody();
        List<String> addedLabels = responseBody.stream()
            .map(label -> (String) label.get("name"))
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("labels", addedLabels);
        result.put("updated_at", LocalDateTime.now().toString());

        return result;
    }

    private List<Map<String, Object>> checkNewIssues(String token,
        Map<String, Object> params, LocalDateTime lastCheck) {
        String repository = getRequiredParam(params, "repository", String.class);

        @SuppressWarnings("unchecked")
        List<String> labelFilter = getOptionalParam(params, "labels", List.class, Collections.emptyList());

        String url = String.format("%s/repos/%s/issues?state=open&sort=created&direction=desc&per_page=10",
                                   GITHUB_API_BASE, repository);

        HttpHeaders headers = createGitHubHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<List<Map<String, Object>>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch GitHub issues: { }", response.getStatusCode());
            return Collections.emptyList();
        }

        List<Map<String, Object>> issues = response.getBody();
        if (issues == null) {
            return Collections.emptyList();
        }

        return issues.stream()
            .filter(issue -> {
                String createdAt = (String) issue.get("created_at");
                LocalDateTime createdDateTime = LocalDateTime.parse(createdAt.substring(0, DATETIME_PREFIX_LENGTH));
                if (createdDateTime.isBefore(lastCheck)) {
                    return false;
                }

                if (!labelFilter.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> issueLabels = (List<Map<String, Object>>) issue.get("labels");
                    List<String> issueLabelNames = issueLabels.stream()
                        .map(label -> (String) label.get("name"))
                        .toList();
                    return issueLabelNames.stream().anyMatch(labelFilter::contains);
                }

                return true;
            })
            .map(issue -> {
                Map<String, Object> event = new HashMap<>();
                event.put("issue_number", issue.get("number"));
                event.put("title", issue.get("title"));
                event.put("body", issue.get("body"));
                @SuppressWarnings("unchecked")
                Map<String, Object> user = (Map<String, Object>) issue.get("user");
                event.put("author", user.get("login"));
                event.put("created_at", issue.get("created_at"));
                event.put("html_url", issue.get("html_url"));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> labels = (List<Map<String, Object>>) issue.get("labels");
                event.put("labels", labels.stream().map(label -> label.get("name")).toList());

                return event;
            })
            .toList();
    }

    private List<Map<String, Object>> checkNewPullRequests(String token,
        Map<String, Object> params, LocalDateTime lastCheck) {
        String repository = getRequiredParam(params, "repository", String.class);
        String targetBranch = getOptionalParam(params, "target_branch", String.class, "main");

        String url = String.format("%s/repos/%s/pulls?state=open&base=%s&sort=created&direction=desc&per_page=10",
                                   GITHUB_API_BASE, repository, targetBranch);

        HttpHeaders headers = createGitHubHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<List<Map<String, Object>>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch GitHub pull requests: { }", response.getStatusCode());
            return Collections.emptyList();
        }

        List<Map<String, Object>> prs = response.getBody();
        if (prs == null) {
            return Collections.emptyList();
        }

        return prs.stream()
            .filter(pr -> {
                String createdAt = (String) pr.get("created_at");
                LocalDateTime createdDateTime = LocalDateTime.parse(createdAt.substring(0, DATETIME_PREFIX_LENGTH));
                return createdDateTime.isAfter(lastCheck);
            })
            .map(pr -> {
                Map<String, Object> event = new HashMap<>();
                event.put("pr_number", pr.get("number"));
                event.put("title", pr.get("title"));
                event.put("body", pr.get("body"));
                @SuppressWarnings("unchecked")
                Map<String, Object> user = (Map<String, Object>) pr.get("user");
                @SuppressWarnings("unchecked")
                Map<String, Object> head = (Map<String, Object>) pr.get("head");
                @SuppressWarnings("unchecked")
                Map<String, Object> base = (Map<String, Object>) pr.get("base");
                event.put("author", user.get("login"));
                event.put("source_branch", head.get("ref"));
                event.put("target_branch", base.get("ref"));
                event.put("created_at", pr.get("created_at"));
                event.put("html_url", pr.get("html_url"));

                return event;
            })
            .toList();
    }

    private List<Map<String, Object>> checkPushToBranch(String token, Map<String,
        Object> params, LocalDateTime lastCheck) {
        String repository = getRequiredParam(params, "repository", String.class);
        String branch = getRequiredParam(params, "branch", String.class);

        String url = String.format("%s/repos/%s/commits?sha=%s&per_page=10",
                                   GITHUB_API_BASE, repository, branch);

        HttpHeaders headers = createGitHubHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<List<Map<String, Object>>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch GitHub commits: { }", response.getStatusCode());
            return Collections.emptyList();
        }

        List<Map<String, Object>> commits = response.getBody();
        if (commits == null) {
            return Collections.emptyList();
        }

        return commits.stream()
            .filter(commit -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> commitData = (Map<String, Object>) commit.get("commit");
                @SuppressWarnings("unchecked")
                Map<String, Object> author = (Map<String, Object>) commitData.get("author");
                String date = (String) author.get("date");
                LocalDateTime commitDateTime = LocalDateTime.parse(date.substring(0, DATETIME_PREFIX_LENGTH));
                return commitDateTime.isAfter(lastCheck);
            })
            .map(commit -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> commitData = (Map<String, Object>) commit.get("commit");
                @SuppressWarnings("unchecked")
                Map<String, Object> author = (Map<String, Object>) commitData.get("author");

                Map<String, Object> event = new HashMap<>();
                event.put("commit_sha", commit.get("sha"));
                event.put("commit_message", commitData.get("message"));
                event.put("author", author.get("name"));
                event.put("branch", branch);
                event.put("pushed_at", author.get("date"));
                event.put("compare_url", commit.get("html_url"));

                return event;
            })
            .toList();
    }

    private String getGitHubToken(UUID userId) {
        Optional<area.server.AREA_Back.entity.User> userOpt =
            userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found: { }", userId);
            return null;
        }
        Optional<UserOAuthIdentity> oauthOpt = userOAuthIdentityRepository
            .findByUserAndProvider(userOpt.get(), GITHUB_PROVIDER_KEY);
        if (oauthOpt.isEmpty()) {
            log.warn("No GitHub OAuth identity found for user: { }", userId);
            return null;
        }

        UserOAuthIdentity oauth = oauthOpt.get();
        String encryptedToken = oauth.getAccessTokenEnc();
        if (encryptedToken == null || encryptedToken.trim().isEmpty()) {
            log.warn("GitHub token is null or empty for user: { }", userId);
            return null;
        }
        try {
            String decryptedToken = tokenEncryptionService.decryptToken(encryptedToken);
            log.debug("GitHub token successfully decrypted for user: {}", userId);
            return decryptedToken;
        } catch (Exception e) {
            log.error("Error decrypting GitHub token for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private HttpHeaders createGitHubHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "AREA-Backend");
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