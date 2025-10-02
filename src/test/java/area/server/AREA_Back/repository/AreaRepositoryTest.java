package area.server.AREA_Back.repository;

import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(locations = "classpath:application-repository-test.properties")
class AreaRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AreaRepository areaRepository;

    private User testUser;
    private Area testArea;

    @BeforeEach
    void setUp() {
        // Create a test user first
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);
        testUser.setCreatedAt(LocalDateTime.now());
        entityManager.persistAndFlush(testUser);

        // Create a test area
        testArea = new Area();
        testArea.setUser(testUser);
        testArea.setName("Test Area");
        testArea.setDescription("Test Description");
        testArea.setEnabled(true);
        testArea.setCreatedAt(LocalDateTime.now());
        testArea.setUpdatedAt(LocalDateTime.now());
        entityManager.persistAndFlush(testArea);
    }

    @Test
    void testFindByUserId() {
        List<Area> userAreas = areaRepository.findByUserId(testUser.getId());
        
        assertEquals(1, userAreas.size());
        assertEquals("Test Area", userAreas.get(0).getName());
        assertEquals(testUser.getId(), userAreas.get(0).getUser().getId());
    }

    @Test
    void testFindByUserIdNotFound() {
        List<Area> userAreas = areaRepository.findByUserId(UUID.randomUUID());
        
        assertTrue(userAreas.isEmpty());
    }

    @Test
    void testFindByNameContainingIgnoreCase() {
        // Create additional areas for testing
        Area area1 = new Area();
        area1.setUser(testUser);
        area1.setName("Email Automation");
        area1.setDescription("Automate email tasks");
        area1.setEnabled(true);
        entityManager.persistAndFlush(area1);

        Area area2 = new Area();
        area2.setUser(testUser);
        area2.setName("Slack Integration");
        area2.setDescription("Slack workflow");
        area2.setEnabled(true);
        entityManager.persistAndFlush(area2);

        // Test case insensitive search
        List<Area> areasWithTest = areaRepository.findByNameContainingIgnoreCase("test");
        assertEquals(1, areasWithTest.size());
        assertEquals("Test Area", areasWithTest.get(0).getName());

        // Test partial match
        List<Area> areasWithEmail = areaRepository.findByNameContainingIgnoreCase("email");
        assertEquals(1, areasWithEmail.size());
        assertEquals("Email Automation", areasWithEmail.get(0).getName());

        // Test case insensitive
        List<Area> areasWithSlack = areaRepository.findByNameContainingIgnoreCase("SLACK");
        assertEquals(1, areasWithSlack.size());
        assertEquals("Slack Integration", areasWithSlack.get(0).getName());

        // Test no match
        List<Area> noMatch = areaRepository.findByNameContainingIgnoreCase("nonexistent");
        assertTrue(noMatch.isEmpty());
    }

    @Test
    void testFindEnabledAreasByUser() {
        // Create an enabled area
        Area enabledArea = new Area();
        enabledArea.setUser(testUser);
        enabledArea.setName("Enabled Area");
        enabledArea.setDescription("This is enabled");
        enabledArea.setEnabled(true);
        entityManager.persistAndFlush(enabledArea);

        // Create a disabled area
        Area disabledArea = new Area();
        disabledArea.setUser(testUser);
        disabledArea.setName("Disabled Area");
        disabledArea.setDescription("This is disabled");
        disabledArea.setEnabled(false);
        entityManager.persistAndFlush(disabledArea);

        List<Area> enabledAreas = areaRepository.findEnabledAreasByUser(testUser);
        
        assertEquals(2, enabledAreas.size()); // testArea + enabledArea
        assertTrue(enabledAreas.stream().allMatch(Area::getEnabled));
        assertTrue(enabledAreas.stream().anyMatch(a -> a.getName().equals("Test Area")));
        assertTrue(enabledAreas.stream().anyMatch(a -> a.getName().equals("Enabled Area")));
        assertFalse(enabledAreas.stream().anyMatch(a -> a.getName().equals("Disabled Area")));
    }

    @Test
    void testCountByUser() {
        // Initially only one area for the test user
        long count = areaRepository.countByUser(testUser);
        assertEquals(1, count);

        // Add another area for the same user
        Area anotherArea = new Area();
        anotherArea.setUser(testUser);
        anotherArea.setName("Another Area");
        anotherArea.setEnabled(true);
        entityManager.persistAndFlush(anotherArea);

        // Count should increase
        long newCount = areaRepository.countByUser(testUser);
        assertEquals(2, newCount);

        // Create another user with areas
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        anotherUser.setIsActive(true);
        anotherUser.setIsAdmin(false);
        entityManager.persistAndFlush(anotherUser);

        Area anotherUserArea = new Area();
        anotherUserArea.setUser(anotherUser);
        anotherUserArea.setName("Another User Area");
        anotherUserArea.setEnabled(true);
        entityManager.persistAndFlush(anotherUserArea);

        // Original user count should remain the same
        long stillTwoCount = areaRepository.countByUser(testUser);
        assertEquals(2, stillTwoCount);

        // New user should have count of 1
        long anotherUserCount = areaRepository.countByUser(anotherUser);
        assertEquals(1, anotherUserCount);
    }

    @Test
    void testSaveArea() {
        Area newArea = new Area();
        newArea.setUser(testUser);
        newArea.setName("New Area");
        newArea.setDescription("New Description");
        newArea.setEnabled(true);
        newArea.setCreatedAt(LocalDateTime.now());

        Area savedArea = areaRepository.save(newArea);
        
        assertNotNull(savedArea.getId());
        assertEquals("New Area", savedArea.getName());
        assertEquals("New Description", savedArea.getDescription());
        assertEquals(testUser.getId(), savedArea.getUser().getId());
        assertTrue(savedArea.getEnabled());
    }

    @Test
    void testUpdateArea() {
        testArea.setName("Updated Test Area");
        testArea.setDescription("Updated Description");
        testArea.setEnabled(false);
        testArea.setUpdatedAt(LocalDateTime.now());
        
        Area updatedArea = areaRepository.save(testArea);
        
        assertEquals(testArea.getId(), updatedArea.getId());
        assertEquals("Updated Test Area", updatedArea.getName());
        assertEquals("Updated Description", updatedArea.getDescription());
        assertFalse(updatedArea.getEnabled());
    }

    @Test
    void testDeleteArea() {
        UUID areaId = testArea.getId();
        
        areaRepository.delete(testArea);
        
        Optional<Area> deletedArea = areaRepository.findById(areaId);
        assertFalse(deletedArea.isPresent());
    }

    @Test
    void testFindById() {
        Optional<Area> foundArea = areaRepository.findById(testArea.getId());
        
        assertTrue(foundArea.isPresent());
        assertEquals(testArea.getId(), foundArea.get().getId());
        assertEquals("Test Area", foundArea.get().getName());
    }

    @Test
    void testFindAll() {
        Area anotherArea = new Area();
        anotherArea.setUser(testUser);
        anotherArea.setName("Another Area");
        anotherArea.setDescription("Another Description");
        anotherArea.setEnabled(true);
        entityManager.persistAndFlush(anotherArea);

        List<Area> allAreas = areaRepository.findAll();
        
        assertEquals(2, allAreas.size());
    }

    @Test
    void testExistsById() {
        boolean exists = areaRepository.existsById(testArea.getId());
        assertTrue(exists);
        
        boolean notExists = areaRepository.existsById(UUID.randomUUID());
        assertFalse(notExists);
    }

    @Test
    void testCount() {
        long count = areaRepository.count();
        assertEquals(1, count);
        
        Area anotherArea = new Area();
        anotherArea.setUser(testUser);
        anotherArea.setName("Count Area");
        anotherArea.setDescription("Count Description");
        anotherArea.setEnabled(true);
        entityManager.persistAndFlush(anotherArea);
        
        long newCount = areaRepository.count();
        assertEquals(2, newCount);
    }

    @Test
    void testMultipleUsersWithAreas() {
        // Create another user
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        anotherUser.setIsActive(true);
        anotherUser.setIsAdmin(false);
        entityManager.persistAndFlush(anotherUser);

        // Create areas for the other user
        Area anotherUserArea1 = new Area();
        anotherUserArea1.setUser(anotherUser);
        anotherUserArea1.setName("Another User Area 1");
        anotherUserArea1.setEnabled(true);
        entityManager.persistAndFlush(anotherUserArea1);

        Area anotherUserArea2 = new Area();
        anotherUserArea2.setUser(anotherUser);
        anotherUserArea2.setName("Another User Area 2");
        anotherUserArea2.setEnabled(false);
        entityManager.persistAndFlush(anotherUserArea2);

        // Test that each user gets only their areas
        List<Area> testUserAreas = areaRepository.findByUserId(testUser.getId());
        assertEquals(1, testUserAreas.size());
        assertEquals("Test Area", testUserAreas.get(0).getName());

        List<Area> anotherUserAreas = areaRepository.findByUserId(anotherUser.getId());
        assertEquals(2, anotherUserAreas.size());
        assertTrue(anotherUserAreas.stream().anyMatch(a -> a.getName().equals("Another User Area 1")));
        assertTrue(anotherUserAreas.stream().anyMatch(a -> a.getName().equals("Another User Area 2")));
    }

    @Test
    void testAreaWithNullDescription() {
        Area areaWithNullDesc = new Area();
        areaWithNullDesc.setUser(testUser);
        areaWithNullDesc.setName("Area with null description");
        areaWithNullDesc.setDescription(null);
        areaWithNullDesc.setEnabled(true);

        Area savedArea = areaRepository.save(areaWithNullDesc);
        
        assertNotNull(savedArea.getId());
        assertEquals("Area with null description", savedArea.getName());
        assertNull(savedArea.getDescription());
    }
}