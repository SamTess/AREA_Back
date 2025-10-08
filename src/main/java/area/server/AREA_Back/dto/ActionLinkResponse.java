package area.server.AREA_Back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionLinkResponse {

    @JsonProperty("sourceActionInstanceId")
    private UUID sourceActionInstanceId;

    @JsonProperty("targetActionInstanceId")
    private UUID targetActionInstanceId;

    @JsonProperty("sourceActionName")
    private String sourceActionName;

    @JsonProperty("targetActionName")
    private String targetActionName;

    @JsonProperty("areaId")
    private UUID areaId;

    @JsonProperty("linkType")
    private String linkType;

    @JsonProperty("mapping")
    private Map<String, Object> mapping;

    @JsonProperty("condition")
    private Map<String, Object> condition;

    @JsonProperty("order")
    private Integer order;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}