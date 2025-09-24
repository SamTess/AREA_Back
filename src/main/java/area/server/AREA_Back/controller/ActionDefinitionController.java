package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.ActionDefinitionResponse;
import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/action-definitions")
@Tag(name = "Action Definitions", description = "API de gestion des définitions d'actions")
public class ActionDefinitionController {

    @Autowired
    private ActionDefinitionRepository actionDefinitionRepository;

    @GetMapping
    @Operation(summary = "Récupérer toutes les définitions d'actions",
               description = "Récupère une liste paginée de toutes les définitions d'actions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Liste des définitions d'actions récupérée avec succès")
    })
    public ResponseEntity<Page<ActionDefinitionResponse>> getAllActionDefinitions(
            @Parameter(description = "Numéro de page (commence à 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Taille de la page")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Champ de tri")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Direction du tri (asc ou desc)")
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ActionDefinition> actionDefinitions = actionDefinitionRepository.findAll(pageable);
        Page<ActionDefinitionResponse> responses = actionDefinitions.map(this::convertToResponse);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer une définition d'action par ID")
    public ResponseEntity<ActionDefinitionResponse> getActionDefinitionById(@PathVariable UUID id) {
        Optional<ActionDefinition> actionDefinition = actionDefinitionRepository.findById(id);
        if (actionDefinition.isPresent()) {
            return ResponseEntity.ok(convertToResponse(actionDefinition.get()));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/service/{serviceKey}")
    @Operation(summary = "Récupérer les définitions d'actions par clé de service")
    public ResponseEntity<List<ActionDefinitionResponse>> getActionDefinitionsByServiceKey(
            @PathVariable String serviceKey) {
        List<ActionDefinition> actionDefinitions = actionDefinitionRepository.findByServiceKey(serviceKey);
        List<ActionDefinitionResponse> responses = actionDefinitions.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/actions")
    @Operation(summary = "Récupérer les actions capables d'émettre des événements")
    public ResponseEntity<List<ActionDefinitionResponse>> getEventCapableActions() {
        List<ActionDefinition> actionDefinitions = actionDefinitionRepository.findEventCapableActions();
        List<ActionDefinitionResponse> responses = actionDefinitions.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/reactions")
    @Operation(summary = "Récupérer les actions exécutables (réactions)")
    public ResponseEntity<List<ActionDefinitionResponse>> getExecutableActions() {
        List<ActionDefinition> actionDefinitions = actionDefinitionRepository.findExecutableActions();
        List<ActionDefinitionResponse> responses = actionDefinitions.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    private ActionDefinitionResponse convertToResponse(ActionDefinition actionDefinition) {
        ActionDefinitionResponse response = new ActionDefinitionResponse();
        response.setId(actionDefinition.getId());
        response.setServiceId(actionDefinition.getService().getId());
        response.setServiceKey(actionDefinition.getService().getKey());
        response.setServiceName(actionDefinition.getService().getName());
        response.setKey(actionDefinition.getKey());
        response.setName(actionDefinition.getName());
        response.setDescription(actionDefinition.getDescription());
        response.setInputSchema(actionDefinition.getInputSchema());
        response.setOutputSchema(actionDefinition.getOutputSchema());
        response.setDocsUrl(actionDefinition.getDocsUrl());
        response.setIsEventCapable(actionDefinition.getIsEventCapable());
        response.setIsExecutable(actionDefinition.getIsExecutable());
        response.setVersion(actionDefinition.getVersion());
        response.setDefaultPollIntervalSeconds(actionDefinition.getDefaultPollIntervalSeconds());
        response.setThrottlePolicy(actionDefinition.getThrottlePolicy());
        response.setCreatedAt(actionDefinition.getCreatedAt());
        response.setUpdatedAt(actionDefinition.getUpdatedAt());
        return response;
    }
}