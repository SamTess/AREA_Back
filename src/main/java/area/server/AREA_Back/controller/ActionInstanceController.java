package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.ActionInstanceResponse;
import area.server.AREA_Back.dto.CreateActionInstanceRequest;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.ActionInstance;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/action-instances")
@Tag(name = "Action Instances", description = "API de gestion des instances d'actions")
public class ActionInstanceController {

    @Autowired
    private ActionInstanceRepository actionInstanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private ActionDefinitionRepository actionDefinitionRepository;

    @Autowired
    private ServiceAccountRepository serviceAccountRepository;

    @GetMapping("/user/{userId}")
    @Operation(summary = "Récupérer les instances d'actions par utilisateur")
    public ResponseEntity<List<ActionInstanceResponse>> getActionInstancesByUser(@PathVariable UUID userId) {
        Optional<User> user = userRepository.findById(userId);
        if (!user.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        List<ActionInstance> actionInstances = actionInstanceRepository.findByUser(user.get());
        List<ActionInstanceResponse> responses = actionInstances.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/area/{areaId}")
    @Operation(summary = "Récupérer les instances d'actions par area")
    public ResponseEntity<List<ActionInstanceResponse>> getActionInstancesByArea(@PathVariable UUID areaId) {
        Optional<Area> area = areaRepository.findById(areaId);
        if (!area.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        List<ActionInstance> actionInstances = actionInstanceRepository.findByArea(area.get());
        List<ActionInstanceResponse> responses = actionInstances.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer une instance d'action par ID")
    public ResponseEntity<ActionInstanceResponse> getActionInstanceById(@PathVariable UUID id) {
        Optional<ActionInstance> actionInstance = actionInstanceRepository.findById(id);
        if (actionInstance.isPresent()) {
            return ResponseEntity.ok(convertToResponse(actionInstance.get()));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    @Operation(summary = "Créer une nouvelle instance d'action")
    public ResponseEntity<ActionInstanceResponse> createActionInstance(
            @Valid @RequestBody CreateActionInstanceRequest request) {

        Optional<Area> area = areaRepository.findById(request.getAreaId());
        if (!area.isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        Optional<ActionDefinition> actionDefinition = actionDefinitionRepository
            .findById(request.getActionDefinitionId());
        if (!actionDefinition.isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        ServiceAccount serviceAccount = null;
        if (request.getServiceAccountId() != null) {
            Optional<ServiceAccount> optionalServiceAccount = serviceAccountRepository
            .findById(request.getServiceAccountId());
            if (!optionalServiceAccount.isPresent()) {
                return ResponseEntity.badRequest().build();
            }
            serviceAccount = optionalServiceAccount.get();
        }

        ActionInstance actionInstance = new ActionInstance();
        actionInstance.setUser(area.get().getUser());
        actionInstance.setArea(area.get());
        actionInstance.setActionDefinition(actionDefinition.get());
        actionInstance.setServiceAccount(serviceAccount);
        actionInstance.setName(request.getName());
        actionInstance.setEnabled(true);
        actionInstance.setParams(request.getParams());

        ActionInstance savedActionInstance = actionInstanceRepository
            .save(actionInstance);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(savedActionInstance));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Activer/désactiver une instance d'action")
    public ResponseEntity<ActionInstanceResponse> toggleActionInstance(@PathVariable UUID id) {
        Optional<ActionInstance> optionalActionInstance = actionInstanceRepository.findById(id);
        if (!optionalActionInstance.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        ActionInstance actionInstance = optionalActionInstance.get();
        actionInstance.setEnabled(!actionInstance.getEnabled());
        ActionInstance updatedActionInstance = actionInstanceRepository.save(actionInstance);

        return ResponseEntity.ok(convertToResponse(updatedActionInstance));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une instance d'action")
    public ResponseEntity<Void> deleteActionInstance(@PathVariable UUID id) {
        if (!actionInstanceRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        actionInstanceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private ActionInstanceResponse convertToResponse(ActionInstance actionInstance) {
        ActionInstanceResponse response = new ActionInstanceResponse();
        response.setId(actionInstance.getId());
        response.setUserId(actionInstance.getUser().getId());
        response.setAreaId(actionInstance.getArea().getId());
        response.setAreaName(actionInstance.getArea().getName());
        response.setActionDefinitionId(actionInstance.getActionDefinition().getId());
        response.setActionDefinitionName(actionInstance.getActionDefinition().getName());
        response.setServiceAccountId(actionInstance.getServiceAccount() != null
            ? actionInstance.getServiceAccount().getId() : null);
        response.setName(actionInstance.getName());
        response.setEnabled(actionInstance.getEnabled());
        response.setParams(actionInstance.getParams());
        response.setCreatedAt(actionInstance.getCreatedAt());
        response.setUpdatedAt(actionInstance.getUpdatedAt());
        return response;
    }
}