package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AreaRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AreaRepository areaRepository;

    private User testUser;
    private Service actionService;
    private Service reactionService;
    private Area testArea;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);

        // Create test services
        actionService = new Service();
        actionService.setName("Gmail");
        actionService.setDescription("Gmail service");
        actionService.setIconUrl("https://gmail.com/icon.png");
        actionService.setEnabled(true);

        reactionService = new Service();
        reactionService.setName("Slack");
        reactionService.setDescription("Slack service");
        reactionService.setIconUrl("https://slack.com/icon.png");
        reactionService.setEnabled(true);

        // Create test area
        testArea = new Area();
        testArea.setName("Email to Slack");
        testArea.setDescription("Forward emails to Slack");
        testArea.setUser(testUser);
        testArea.setActionService(actionService);
        testArea.setActionType("new_email");
        testArea.setActionConfig("{\"folder\": \"inbox\"}");
        testArea.setReactionService(reactionService);
        testArea.setReactionType("send_message");
        testArea.setReactionConfig("{\"channel\": \"#general\"}");
        testArea.setEnabled(true);

        // Persist entities
        entityManager.persistAndFlush(testUser);
        entityManager.persistAndFlush(actionService);
        entityManager.persistAndFlush(reactionService);
    }

    @Test
    void shouldFindAreasByUser() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> areas = areaRepository.findByUser(testUser);

        // Then
        assertThat(areas).hasSize(1);
        assertThat(areas.get(0).getName()).isEqualTo("Email to Slack");
    }

    @Test
    void shouldFindAreasByUserId() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> areas = areaRepository.findByUserId(testUser.getId());

        // Then
        assertThat(areas).hasSize(1);
        assertThat(areas.get(0).getName()).isEqualTo("Email to Slack");
    }

    @Test
    void shouldFindEnabledAreasByUser() {
        // Given
        Area disabledArea = new Area();
        disabledArea.setName("Disabled Area");
        disabledArea.setUser(testUser);
        disabledArea.setActionService(actionService);
        disabledArea.setActionType("test_action");
        disabledArea.setReactionService(reactionService);
        disabledArea.setReactionType("test_reaction");
        disabledArea.setEnabled(false);

        entityManager.persistAndFlush(testArea);
        entityManager.persistAndFlush(disabledArea);

        // When
        List<Area> enabledAreas = areaRepository.findEnabledAreasByUser(testUser);

        // Then
        assertThat(enabledAreas).hasSize(1);
        assertThat(enabledAreas.get(0).getName()).isEqualTo("Email to Slack");
    }

    @Test
    void shouldFindAreasByActionService() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> areas = areaRepository.findByActionService(actionService);

        // Then
        assertThat(areas).hasSize(1);
        assertThat(areas.get(0).getActionType()).isEqualTo("new_email");
    }

    @Test
    void shouldFindAreasByReactionService() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> areas = areaRepository.findByReactionService(reactionService);

        // Then
        assertThat(areas).hasSize(1);
        assertThat(areas.get(0).getReactionType()).isEqualTo("send_message");
    }

    @Test
    void shouldFindAreasByActionType() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> areas = areaRepository.findByActionType("new_email");

        // Then
        assertThat(areas).hasSize(1);
        assertThat(areas.get(0).getName()).isEqualTo("Email to Slack");
    }

    @Test
    void shouldFindAreasByReactionType() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> areas = areaRepository.findByReactionType("send_message");

        // Then
        assertThat(areas).hasSize(1);
        assertThat(areas.get(0).getName()).isEqualTo("Email to Slack");
    }

    @Test
    void shouldFindRecentlyTriggeredAreas() {
        // Given
        testArea.setLastTriggered(LocalDateTime.now().minusMinutes(30));
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> recentlyTriggered = areaRepository.findRecentlyTriggeredAreas(
            LocalDateTime.now().minusHours(1)
        );

        // Then
        assertThat(recentlyTriggered).hasSize(1);
        assertThat(recentlyTriggered.get(0).getName()).isEqualTo("Email to Slack");
    }

    @Test
    void shouldFindAreasToTrigger() {
        // Given
        Area oldArea = new Area();
        oldArea.setName("Old Area");
        oldArea.setUser(testUser);
        oldArea.setActionService(actionService);
        oldArea.setActionType("old_action");
        oldArea.setReactionService(reactionService);
        oldArea.setReactionType("old_reaction");
        oldArea.setEnabled(true);
        oldArea.setLastTriggered(LocalDateTime.now().minusHours(2));

        entityManager.persistAndFlush(testArea); // No lastTriggered set (null)
        entityManager.persistAndFlush(oldArea);

        // When
        List<Area> areasToTrigger = areaRepository.findAreasToTrigger(
            LocalDateTime.now().minusHours(1)
        );

        // Then
        assertThat(areasToTrigger).hasSize(2); // Both areas should be triggered
    }

    @Test
    void shouldCountAreasByUser() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        long count = areaRepository.countByUser(testUser);

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldFindAreasByNameContainingIgnoreCase() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> areas = areaRepository.findByNameContainingIgnoreCase("email");

        // Then
        assertThat(areas).hasSize(1);
        assertThat(areas.get(0).getName()).isEqualTo("Email to Slack");
    }

    @Test
    void shouldReturnEmptyListWhenNoAreasMatchNameSearch() {
        // Given
        entityManager.persistAndFlush(testArea);

        // When
        List<Area> areas = areaRepository.findByNameContainingIgnoreCase("nonexistent");

        // Then
        assertThat(areas).isEmpty();
    }
}