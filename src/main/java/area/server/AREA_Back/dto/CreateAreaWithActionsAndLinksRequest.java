package area.server.AREA_Back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAreaWithActionsAndLinksRequest {

    @NotBlank(message = "Area name is required")
    private String name;

    private String description;

    private UUID userId;

    @Valid
    @NotEmpty(message = "At least one action is required")
    private List<AreaActionRequest> actions;

    @Valid
    private List<AreaReactionRequest> reactions;

    @Valid
    @JsonProperty("connections")
    private List<ActionConnectionRequest> connections;

    @JsonProperty("layoutMode")
    private String layoutMode = "linear"; // "linear" or "free"

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionConnectionRequest {

        @JsonProperty("sourceServiceId")
        private String sourceServiceId;

        @JsonProperty("targetServiceId")
        private String targetServiceId;

        @JsonProperty("linkType")
        private String linkType = "chain";

        @JsonProperty("mapping")
        private Map<String, Object> mapping;

        @JsonProperty("condition")
        private Map<String, Object> condition;

        @JsonProperty("order")
        private Integer order = 0;
    }
}