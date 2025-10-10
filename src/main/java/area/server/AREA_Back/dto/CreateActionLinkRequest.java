package area.server.AREA_Back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateActionLinkRequest {

    @NotNull(message = "Source action instance ID is required")
    @JsonProperty("sourceActionInstanceId")
    private UUID sourceActionInstanceId;

    @NotNull(message = "Target action instance ID is required")
    @JsonProperty("targetActionInstanceId")
    private UUID targetActionInstanceId;

    @JsonProperty("linkType")
    private String linkType = "chain"; //* chain, conditional, parallel, sequential

    @JsonProperty("mapping")
    private Map<String, Object> mapping;

    @JsonProperty("condition")
    private Map<String, Object> condition;

    @JsonProperty("order")
    private Integer order = 0;
}