package area.server.AREA_Back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaDraftRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Area name is required")
    private String name;

    private String description;

    @Valid
    private List<AreaActionRequest> actions;

    @Valid
    private List<AreaReactionRequest> reactions;

    @Valid
    @JsonProperty("connections")
    private List<ConnectionRequest> connections;

    @JsonProperty("layoutMode")
    private String layoutMode = "linear";

    @JsonProperty("draftId")
    private String draftId;

    @JsonProperty("savedAt")
    private LocalDateTime savedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionRequest implements Serializable {

        private static final long serialVersionUID = 1L;

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
