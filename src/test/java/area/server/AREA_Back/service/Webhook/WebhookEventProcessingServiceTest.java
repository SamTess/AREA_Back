package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.*;
import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.ActionLinkRepository;
import area.server.AREA_Back.repository.ActivationModeRepository;
import area.server.AREA_Back.service.Area.ExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookEventProcessingServiceTest {

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ActivationModeRepository activationModeRepository;

    @Mock
    private ActionLinkRepository actionLinkRepository;

    @Mock
    private ExecutionService executionService;

    @Mock
    private PayloadMappingService payloadMappingService;

    @InjectMocks
    private WebhookEventProcessingService webhookEventProcessingService;

    private UUID userId;
    private ActionInstance actionInstance;
    private ActionDefinition actionDefinition;
    private area.server.AREA_Back.entity.Service service;
    private ActivationMode webhookActivationMode;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        service = new area.server.AREA_Back.entity.Service();
        service.setKey("github");
        service.setName("GitHub");

        actionDefinition = new ActionDefinition();
        actionDefinition.setKey("new_issue");
        actionDefinition.setService(service);

        actionInstance = new ActionInstance();
        actionInstance.setId(UUID.randomUUID());
        actionInstance.setActionDefinition(actionDefinition);

        webhookActivationMode = new ActivationMode();
        webhookActivationMode.setType(ActivationModeType.WEBHOOK);
        webhookActivationMode.setEnabled(true);
        webhookActivationMode.setActionInstance(actionInstance);
    }

    @Test
    void testProcessWebhookEvent_WithUserId() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEvent_WithoutUserId() {
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, null
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEventForUser() {
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEventForUser(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEventGlobally() {
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEventGlobally(
            "github", "new_issue", payload
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEvent_NoMatchingInstances() {
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.emptyList());

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "unknown_action", payload, userId
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testProcessWebhookEvent_NoWebhookActivationMode() {
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.emptyList());

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testProcessWebhookEvent_GitHubIssues() {
        actionDefinition.setKey("new_issue");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "issues", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEvent_GitHubPullRequest() {
        actionDefinition.setKey("new_pull_request");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "pull_request", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEvent_GitHubPush() {
        actionDefinition.setKey("push_to_branch");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "push", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEvent_SlackMessage() {
        service.setKey("slack");
        actionDefinition.setKey("new_message");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "message", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEvent_SlackReaction() {
        service.setKey("slack");
        actionDefinition.setKey("reaction_added");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "reaction_added", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testGenerateDeduplicationKey() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "12345");

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "github", "issues", payload
        );

        assertNotNull(key);
        assertTrue(key.contains("github"));
        assertTrue(key.contains("issues"));
    }

    @Test
    void testGenerateDeduplicationKey_WithGitHubPayload() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> issue = new HashMap<>();
        issue.put("id", 123);
        payload.put("issue", issue);

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "github", "issues", payload
        );

        assertNotNull(key);
    }

    @Test
    void testGenerateDeduplicationKey_WithGitHubPRPayload() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> pullRequest = new HashMap<>();
        pullRequest.put("id", 456);
        payload.put("pull_request", pullRequest);

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "github", "pull_request", payload
        );

        assertNotNull(key);
    }

    @Test
    void testGenerateDeduplicationKey_NoEventId() {
        Map<String, Object> payload = new HashMap<>();

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "github", "unknown", payload
        );

        assertNotNull(key);
    }

    @Test
    void testProcessWebhookEvent_MultipleInstances() {
        ActionInstance instance2 = new ActionInstance();
        instance2.setId(UUID.randomUUID());
        instance2.setActionDefinition(actionDefinition);

        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Arrays.asList(actionInstance, instance2));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEvent_EmptyPayload() {
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testProcessWebhookEvent_DiscordService() {
        service.setKey("discord");
        actionDefinition.setKey("new_message");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findAll())
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "message", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesGitHubAction_IssueUpdated() {
        actionDefinition.setKey("issue_updated");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "issue", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesGitHubAction_PullRequestUpdated() {
        actionDefinition.setKey("pr_updated");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "pull_requests", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesGitHubAction_CommitPushed() {
        actionDefinition.setKey("commit_pushed");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "push", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesGitHubAction_NewRelease() {
        actionDefinition.setKey("new_release");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "release", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesGitHubAction_RepositoryStarred() {
        actionDefinition.setKey("repository_starred");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "star", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesGitHubAction_RepositoryForked() {
        actionDefinition.setKey("repository_forked");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "fork", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesGitHubAction_Ping() {
        actionDefinition.setKey("any_action");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "ping", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesSlackAction_MessagePosted() {
        service.setKey("slack");
        actionDefinition.setKey("message_posted");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "slack"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "message", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesSlackAction_MemberJoined() {
        service.setKey("slack");
        actionDefinition.setKey("member_joined");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "slack"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "member_joined_channel", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesSlackAction_AppMention() {
        service.setKey("slack");
        actionDefinition.setKey("app_mention");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "slack"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "app_mention", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesSlackAction_Default() {
        service.setKey("slack");
        actionDefinition.setKey("custom_event");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "slack"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "custom_event", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesDiscordAction_MessageCreated() {
        service.setKey("discord");
        actionDefinition.setKey("message_created");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "message_create", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesDiscordAction_ReactionAdded() {
        service.setKey("discord");
        actionDefinition.setKey("reaction_added");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "message_reaction_add", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesDiscordAction_MemberJoined() {
        service.setKey("discord");
        actionDefinition.setKey("member_joined");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "guild_member_add", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesDiscordAction_ChannelCreated() {
        service.setKey("discord");
        actionDefinition.setKey("channel_created");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "channel_create", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesDiscordAction_ServerJoined() {
        service.setKey("discord");
        actionDefinition.setKey("server_joined");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "guild_create", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesDiscordAction_Default() {
        service.setKey("discord");
        actionDefinition.setKey("unknown_action");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "unknown_action", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesActionType_FallbackContains() {
        service.setKey("custom_service");
        actionDefinition.setKey("custom_action_test");
        
        Map<String, Object> payload = new HashMap<>();

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "custom_service"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(any(), anyBoolean()))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "custom_service", "test", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testCreateExecutionForWebhook_WithActionLinks() {
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);
        
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("field1", "value1");
        actionLink.setMapping(mapping);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);
        chainMode.setActionInstance(targetInstance);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);
        when(payloadMappingService.applyMapping(any(), any()))
            .thenReturn(payload);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
        verify(executionService, times(1)).createExecution(any(), any(), any(), any());
    }

    @Test
    void testCreateExecutionForWebhook_NoChainActivationMode() {
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.emptyList());

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
        verify(executionService, never()).createExecution(any(), any(), any(), any());
    }

    @Test
    void testCreateExecutionForWebhook_WithMappingException() {
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);
        
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("field1", "value1");
        actionLink.setMapping(mapping);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);
        chainMode.setActionInstance(targetInstance);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(payloadMappingService.applyMapping(any(), any()))
            .thenThrow(new RuntimeException("Mapping failed"));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testCreateExecutionForWebhook_ThrowsException() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenThrow(new RuntimeException("Database error"));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_GitHub_IssueUpdatedEdited() {
        actionDefinition.setKey("issue_updated");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "edited");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "issue_updated", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_GitHub_IssueUpdatedLabeled() {
        actionDefinition.setKey("issue_updated");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "labeled");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "issue_updated", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_GitHub_IssueClosed() {
        actionDefinition.setKey("issue_closed");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "closed");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "issue_closed", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_GitHub_PRUpdatedSynchronize() {
        actionDefinition.setKey("pr_updated");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "synchronize");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "pr_updated", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_GitHub_PRMerged() {
        actionDefinition.setKey("pr_merged");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "closed");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "pr_merged", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Slack_NewMessage() {
        service.setKey("slack");
        actionDefinition.setKey("new_message");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "message");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "slack"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "new_message", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Slack_ReactionAdded() {
        service.setKey("slack");
        actionDefinition.setKey("reaction_added");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "reaction_added");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "slack"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "reaction_added", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Slack_MemberJoined() {
        service.setKey("slack");
        actionDefinition.setKey("member_joined");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "member_joined_channel");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "slack"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "slack", "member_joined", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Discord_MessageCreate() {
        service.setKey("discord");
        actionDefinition.setKey("new_message");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "message_create");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "new_message", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Discord_MessageCreateUpperCase() {
        service.setKey("discord");
        actionDefinition.setKey("new_message");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "MESSAGE_CREATE");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "new_message", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Discord_MessageReaction() {
        service.setKey("discord");
        actionDefinition.setKey("message_reaction");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "message_reaction_add");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "message_reaction", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Discord_MessageReactionUpperCase() {
        service.setKey("discord");
        actionDefinition.setKey("message_reaction");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "MESSAGE_REACTION_ADD");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "message_reaction", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Discord_NewMember() {
        service.setKey("discord");
        actionDefinition.setKey("new_member");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "guild_member_add");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "new_member", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Discord_NewMemberUpperCase() {
        service.setKey("discord");
        actionDefinition.setKey("new_member");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "GUILD_MEMBER_ADD");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "new_member", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Discord_ChannelCreated() {
        service.setKey("discord");
        actionDefinition.setKey("channel_created");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "channel_create");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "channel_created", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_Discord_ChannelCreatedUpperCase() {
        service.setKey("discord");
        actionDefinition.setKey("channel_created");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "CHANNEL_CREATE");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "discord"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "discord", "channel_created", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testMatchesEventSubType_NonMatchingAction() {
        actionDefinition.setKey("new_issue");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "closed");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "github"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "github", "new_issue", payload, userId
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMatchesEventSubType_UnknownService() {
        service.setKey("unknown_service");
        actionDefinition.setKey("some_action");
        ActionLink actionLink = new ActionLink();
        actionLink.setSourceActionInstance(actionInstance);
        
        ActionInstance targetInstance = new ActionInstance();
        targetInstance.setId(UUID.randomUUID());
        targetInstance.setActionDefinition(actionDefinition);
        actionLink.setTargetActionInstance(targetInstance);

        ActivationMode chainMode = new ActivationMode();
        chainMode.setType(ActivationModeType.CHAIN);
        chainMode.setEnabled(true);

        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "test_action");

        when(actionInstanceRepository.findEnabledActionInstancesByUserAndService(userId, "unknown_service"))
            .thenReturn(Collections.singletonList(actionInstance));
        when(activationModeRepository.findByActionInstanceAndEnabled(actionInstance, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(actionInstance, ActivationModeType.WEBHOOK, true))
            .thenReturn(Collections.singletonList(webhookActivationMode));
        when(actionLinkRepository.findBySourceActionInstanceIdWithTargetFetch(actionInstance.getId()))
            .thenReturn(Collections.singletonList(actionLink));
        when(activationModeRepository.findByActionInstanceAndTypeAndEnabled(targetInstance, ActivationModeType.CHAIN, true))
            .thenReturn(Collections.singletonList(chainMode));
        when(executionService.createExecution(any(), any(), any(), any()))
            .thenReturn(execution);

        List<Execution> result = webhookEventProcessingService.processWebhookEvent(
            "unknown_service", "some_action", payload, userId
        );

        assertNotNull(result);
    }

    @Test
    void testExtractEventId_SlackService() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", "slack-123");

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "slack", "message", payload
        );

        assertNotNull(key);
        assertTrue(key.contains("slack-123"));
    }

    @Test
    void testExtractEventId_DiscordService() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "discord-456");

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "discord", "message_create", payload
        );

        assertNotNull(key);
        assertTrue(key.contains("discord-456"));
    }

    @Test
    void testExtractEventId_DefaultService() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "default-789");

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "custom", "event", payload
        );

        assertNotNull(key);
        assertTrue(key.contains("default-789"));
    }

    @Test
    void testExtractGitHubEventId_NullIssue() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("issue", null);

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "github", "issues", payload
        );

        assertNotNull(key);
    }

    @Test
    void testExtractGitHubEventId_NullPullRequest() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pull_request", null);

        String key = webhookEventProcessingService.generateDeduplicationKey(
            "github", "pull_request", payload
        );

        assertNotNull(key);
    }
}
