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
public class CreateActionInstanceRequest {

    @NotNull(message = "Area ID is required")
    private UUID areaId;

    @NotNull(message = "Action definition ID is required")
    private UUID actionDefinitionId;

    private UUID serviceAccountId;

    @NotBlank(message = "Name is required")
    private String name;

    private Map<String, Object> params;
}