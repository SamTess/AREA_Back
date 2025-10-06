package area.server.AREA_Back.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JsonSchemaValidationService {

    public void validateParameters(Map<String, Object> inputSchema, Map<String, Object> parameters) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            log.debug("No input schema provided, skipping validation");
            return;
        }
        if (inputSchema.containsKey("required") && inputSchema.get("required") instanceof Iterable<?>) {
            @SuppressWarnings("unchecked")
            Iterable<String> requiredFields = (Iterable<String>) inputSchema.get("required");

            for (String requiredField : requiredFields) {
                if (parameters == null || !parameters.containsKey(requiredField)) {
                    throw new IllegalArgumentException("Required parameter '" + requiredField + "' is missing");
                }

                Object value = parameters.get(requiredField);
                if (value == null) {
                    throw new IllegalArgumentException("Required parameter '" + requiredField + "' cannot be null");
                }
            }
        }

        log.debug("Basic JSON schema validation passed");
    }

    public String validateParametersQuietly(Map<String, Object> inputSchema, Map<String, Object> parameters) {
        try {
            validateParameters(inputSchema, parameters);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}