package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubActionServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @InjectMocks
    private GitHubActionService gitHubActionService;

    private UUID testUserId;
    private UserOAuthIdentity mockOAuthIdentity;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        mockOAuthIdentity = new UserOAuthIdentity();
        mockOAuthIdentity.setProvider("github");
        mockOAuthIdentity.setProviderUserId("123456");
        mockOAuthIdentity.setAccessTokenEnc("mock_token_encrypted");
    }

    @Test
    void testExecuteGitHubAction_WithoutToken_ShouldThrowException() {
        // Given
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("github", testUserId.toString()))
            .thenReturn(Optional.empty());

        Map<String, Object> params = Map.of(
            "repository", "owner/repo",
            "title", "Test Issue"
        );

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            gitHubActionService.executeGitHubAction("create_issue", Map.of(), params, testUserId)
        );

        assertTrue(exception.getMessage().contains("No GitHub token found"));
    }

    @Test
    void testExecuteGitHubAction_UnknownAction_ShouldThrowException() {
        // Given
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("github", testUserId.toString()))
            .thenReturn(Optional.of(mockOAuthIdentity));

        Map<String, Object> params = Map.of("repository", "owner/repo");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            gitHubActionService.executeGitHubAction("unknown_action", Map.of(), params, testUserId)
        );

        assertTrue(exception.getMessage().contains("GitHub action execution failed"));
    }

    @Test
    void testCheckGitHubEvents_WithoutToken_ShouldReturnEmptyList() {
        // Given
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("github", testUserId.toString()))
            .thenReturn(Optional.empty());

        Map<String, Object> params = Map.of("repository", "owner/repo");

        // When
        List<Map<String, Object>> events = gitHubActionService.checkGitHubEvents(
            "new_issue", params, testUserId, java.time.LocalDateTime.now().minusHours(1)
        );

        // Then
        assertTrue(events.isEmpty());
    }

    @Test
    void testCheckGitHubEvents_UnknownEvent_ShouldReturnEmptyList() {
        // Given
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("github", testUserId.toString()))
            .thenReturn(Optional.of(mockOAuthIdentity));

        Map<String, Object> params = Map.of("repository", "owner/repo");

        // When
        List<Map<String, Object>> events = gitHubActionService.checkGitHubEvents(
            "unknown_event", params, testUserId, java.time.LocalDateTime.now().minusHours(1)
        );

        // Then
        assertTrue(events.isEmpty());
    }
}