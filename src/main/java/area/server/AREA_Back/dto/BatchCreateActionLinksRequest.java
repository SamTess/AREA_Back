package area.server.AREA_Back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchCreateActionLinksRequest {

    @JsonProperty("areaId")
    private UUID areaId;

    @JsonProperty("links")
    private List<ActionLinkData> links;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionLinkData {

        @JsonProperty("sourceActionInstanceId")
        private UUID sourceActionInstanceId;

        @JsonProperty("targetActionInstanceId")
        private UUID targetActionInstanceId;

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