package area.server.AREA_Back.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreateAreaWithActionsRequest - Tests Unitaires")
class CreateAreaWithActionsRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Doit créer une requête valide")
    void shouldCreateValidRequest() {
        // Given
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setName("Test Area");
        request.setUserId(UUID.randomUUID());

        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        request.setActions(List.of(action));

        request.setReactions(new ArrayList<>());

        // When
        Set<ConstraintViolation<CreateAreaWithActionsRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Doit échouer si name est vide")
    void shouldFailWhenNameIsBlank() {
        // Given
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setName("");

        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test");
        request.setActions(List.of(action));

        // When
        Set<ConstraintViolation<CreateAreaWithActionsRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Area name is required")));
    }

    @Test
    @DisplayName("Doit échouer si actions est vide")
    void shouldFailWhenActionsIsEmpty() {
        // Given
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setName("Test");
        request.setActions(new ArrayList<>());

        // When
        Set<ConstraintViolation<CreateAreaWithActionsRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("At least one action is required")));
    }

    @Test
    @DisplayName("Doit accepter description null")
    void shouldAcceptNullDescription() {
        // Given
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setName("Test");
        request.setDescription(null);

        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test");
        request.setActions(List.of(action));

        // When
        Set<ConstraintViolation<CreateAreaWithActionsRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Doit gérer plusieurs actions")
    void shouldHandleMultipleActions() {
        // Given
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setName("Test");

        List<AreaActionRequest> actions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AreaActionRequest action = new AreaActionRequest();
            action.setActionDefinitionId(UUID.randomUUID());
            action.setName("Action " + i);
            actions.add(action);
        }
        request.setActions(actions);

        // When
        Set<ConstraintViolation<CreateAreaWithActionsRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(3, request.getActions().size());
    }

    @Test
    @DisplayName("Doit gérer plusieurs réactions")
    void shouldHandleMultipleReactions() {
        // Given
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest();
        request.setName("Test");

        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Action");
        request.setActions(List.of(action));

        List<AreaReactionRequest> reactions = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            AreaReactionRequest reaction = new AreaReactionRequest();
            reaction.setActionDefinitionId(UUID.randomUUID());
            reaction.setName("Reaction " + i);
            reactions.add(reaction);
        }
        request.setReactions(reactions);

        // When
        Set<ConstraintViolation<CreateAreaWithActionsRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
        assertEquals(2, request.getReactions().size());
    }

    @Test
    @DisplayName("Doit gérer le constructeur all-args")
    void shouldHandleAllArgsConstructor() {
        // Given
        String name = "Test";
        String description = "Description";
        UUID userId = UUID.randomUUID();
        List<AreaActionRequest> actions = new ArrayList<>();
        List<AreaReactionRequest> reactions = new ArrayList<>();

        // When
        CreateAreaWithActionsRequest request = new CreateAreaWithActionsRequest(
            name, description, userId, actions, reactions
        );

        // Then
        assertEquals(name, request.getName());
        assertEquals(description, request.getDescription());
        assertEquals(userId, request.getUserId());
        assertEquals(actions, request.getActions());
        assertEquals(reactions, request.getReactions());
    }
}
