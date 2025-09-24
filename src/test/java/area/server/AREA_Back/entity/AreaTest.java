package area.server.AREA_Back.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaTest {

    private Validator validator;
    private Area area;
    private User user;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        // Create a valid user first
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setIsActive(true);
        user.setIsAdmin(false);

        // Create a valid area
        area = new Area();
        area.setId(UUID.randomUUID());
        area.setUser(user);
        area.setName("Test Area");
        area.setDescription("Test Description");
        area.setEnabled(true);
        area.setCreatedAt(LocalDateTime.now());
        area.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testValidArea() {
        Set<ConstraintViolation<Area>> violations = validator.validate(area);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testAreaWithBlankName() {
        area.setName("");
        Set<ConstraintViolation<Area>> violations = validator.validate(area);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Area name is required")));
    }

    @Test
    void testAreaWithNullName() {
        area.setName(null);
        Set<ConstraintViolation<Area>> violations = validator.validate(area);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Area name is required")));
    }

    @Test
    void testAreaWithNullUser() {
        area.setUser(null);
        Set<ConstraintViolation<Area>> violations = validator.validate(area);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("User is required")));
    }

    @Test
    void testAreaDefaultValues() {
        Area newArea = new Area();
        newArea.setUser(user);
        newArea.setName("Default Area");

        assertTrue(newArea.getEnabled());
    }

    @Test
    void testAreaEqualsAndHashCode() {
        Area area1 = new Area();
        area1.setId(UUID.randomUUID());
        area1.setUser(user);
        area1.setName("Test Area");

        Area area2 = new Area();
        area2.setId(area1.getId());
        area2.setUser(user);
        area2.setName("Test Area");

        assertEquals(area1, area2);
        assertEquals(area1.hashCode(), area2.hashCode());
    }

    @Test
    void testAreaToString() {
        String areaString = area.toString();
        assertNotNull(areaString);
        assertTrue(areaString.contains("Test Area"));
    }

    @Test
    void testAreaSettersAndGetters() {
        UUID id = UUID.randomUUID();
        User newUser = new User();
        newUser.setId(UUID.randomUUID());
        newUser.setEmail("newuser@example.com");

        String name = "New Area";
        String description = "New Description";
        Boolean enabled = false;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now().plusHours(1);

        area.setId(id);
        area.setUser(newUser);
        area.setName(name);
        area.setDescription(description);
        area.setEnabled(enabled);
        area.setCreatedAt(createdAt);
        area.setUpdatedAt(updatedAt);

        assertEquals(id, area.getId());
        assertEquals(newUser, area.getUser());
        assertEquals(name, area.getName());
        assertEquals(description, area.getDescription());
        assertEquals(enabled, area.getEnabled());
        assertEquals(createdAt, area.getCreatedAt());
        assertEquals(updatedAt, area.getUpdatedAt());
    }

    @Test
    void testAreaConstructors() {
        // Test no-args constructor
        Area area1 = new Area();
        assertNotNull(area1);

        // Test using setters after construction
        Area area2 = new Area();
        area2.setUser(user);
        area2.setName("Constructor Area");
        area2.setDescription("Constructor Description");

        assertEquals(user, area2.getUser());
        assertEquals("Constructor Area", area2.getName());
        assertEquals("Constructor Description", area2.getDescription());
    }

    @Test
    void testAreaWithNullDescription() {
        area.setDescription(null);
        Set<ConstraintViolation<Area>> violations = validator.validate(area);
        assertTrue(violations.isEmpty()); // Description can be null
    }

    @Test
    void testAreaEnabledToggle() {
        area.setEnabled(true);
        assertTrue(area.getEnabled());

        area.setEnabled(false);
        assertFalse(area.getEnabled());
    }
}