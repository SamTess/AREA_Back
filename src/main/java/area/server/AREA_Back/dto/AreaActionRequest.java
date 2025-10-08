package area.server.AREA_Back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaActionRequest {

    @NotNull(message = "Action definition ID is required")
    private UUID actionDefinitionId;

    @NotBlank(message = "Action name is required")
    private String name;

    private String description;

    private UUID serviceAccountId;

    private Map<String, Object> parameters;

    private Map<String, Object> activationConfig;
}