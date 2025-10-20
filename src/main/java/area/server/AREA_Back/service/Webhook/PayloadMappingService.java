package area.server.AREA_Back.service.Webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayloadMappingService {

    private final ObjectMapper objectMapper;

    public Map<String, Object> applyMapping(Map<String, Object> sourcePayload, String mappingJson) {
        if (mappingJson == null || mappingJson.trim().isEmpty()) {
            log.debug("No mapping configuration provided, returning original payload");
            return sourcePayload;
        }

        try {
            JsonNode mappingNode = objectMapper.readTree(mappingJson);
            Map<String, Object> result = new HashMap<>();

            Iterator<Map.Entry<String, JsonNode>> fields = mappingNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String targetField = field.getKey();
                String sourceField = field.getValue().asText();

                Object value = extractValue(sourcePayload, sourceField);
                if (value != null) {
                    result.put(targetField, value);
                    log.debug("Mapped field {} -> {}: {}", sourceField, targetField, value);
                } else {
                    log.debug("Source field {} not found or null", sourceField);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("Error applying payload mapping: {}", e.getMessage(), e);
            return sourcePayload;
        }
    }

    public Map<String, Object> applyMappingWithMerge(Map<String, Object> sourcePayload, String mappingJson) {
        Map<String, Object> mapped = applyMapping(sourcePayload, mappingJson);

        if (mapped.isEmpty()) {
            return sourcePayload;
        }

        Map<String, Object> result = new HashMap<>(sourcePayload);
        result.putAll(mapped);

        return result;
    }

    private Object extractValue(Map<String, Object> map, String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> currentMap = (Map<String, Object>) current;
                current = currentMap.get(part);
            } else {
                try {
                    current = getPropertyValue(current, part);
                } catch (Exception e) {
                    log.debug("Could not access property {} on object: {}", part, e.getMessage());
                    return null;
                }
            }
        }

        return current;
    }

    private Object getPropertyValue(Object obj, String propertyName) throws Exception {
        if (obj == null) {
            return null;
        }

        String getterName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        try {
            java.lang.reflect.Method getter = obj.getClass().getMethod(getterName);
            return getter.invoke(obj);
        } catch (NoSuchMethodException e) {
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(propertyName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException ex) {
                log.debug("Property {} not found on object of type {}", propertyName, obj.getClass().getName());
                return null;
            }
        }
    }

    public String createGitHubIssueToCommentMapping() {
        return """
            {
                "issue_number": "issue.number",
                "repository": "repository.full_name",
                "issue_title": "issue.title",
                "issue_url": "issue.html_url"
            }
            """;
    }

    public String createGitHubPRMapping() {
        return """
            {
                "pr_number": "pull_request.number",
                "repository": "repository.full_name",
                "pr_title": "pull_request.title",
                "pr_url": "pull_request.html_url",
                "pr_state": "pull_request.state"
            }
            """;
    }
}
