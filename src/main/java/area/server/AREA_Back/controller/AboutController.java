package area.server.AREA_Back.controller;

import area.server.AREA_Back.entity.ActionDefinition;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/")
@Tag(name = "About", description = "Information about available services and actions")
public class AboutController {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ActionDefinitionRepository actionDefinitionRepository;

    @GetMapping("/about.json")
    @Operation(summary = "Information about available services",
               description = "Returns the list of services, actions and reactions available in the API")
    public ResponseEntity<Map<String, Object>> getAbout() {
        Map<String, Object> about = new HashMap<>();
        about.put("client", Map.of("host", "localhost:8080"));
        about.put("server", Map.of("current_time", LocalDateTime.now().toString()));
        about.put("services", buildServicesInfo());

        return ResponseEntity.ok(about);
    }

    private List<Map<String, Object>> buildServicesInfo() {
        List<Service> services = serviceRepository.findAllEnabledServices();

        return services.stream().map(service -> {
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("name", service.getName());
            serviceInfo.put("key", service.getKey());

            List<ActionDefinition> actions = actionDefinitionRepository
                .findByServiceKey(service.getKey())
                .stream()
                .filter(ActionDefinition::getIsEventCapable)
                .collect(Collectors.toList());

            serviceInfo.put("actions", actions.stream().map(this::mapActionDefinition).collect(Collectors.toList()));

            List<ActionDefinition> reactions = actionDefinitionRepository
                .findByServiceKey(service.getKey())
                .stream()
                .filter(ActionDefinition::getIsExecutable)
                .collect(Collectors.toList());

            serviceInfo.put("reactions", reactions.stream()
                .map(this::mapActionDefinition)
                .collect(Collectors.toList()));

            return serviceInfo;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> mapActionDefinition(ActionDefinition actionDef) {
        Map<String, Object> mapped = new HashMap<>();
        mapped.put("name", actionDef.getName());
        mapped.put("key", actionDef.getKey());
        mapped.put("description", actionDef.getDescription());
        return mapped;
    }
}