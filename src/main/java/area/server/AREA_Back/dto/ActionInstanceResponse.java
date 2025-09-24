package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionInstanceResponse {
    private UUID id;
    private UUID userId;
    private UUID areaId;
    private String areaName;
    private UUID actionDefinitionId;
    private String actionDefinitionName;
    private UUID serviceAccountId;
    private String name;
    private Boolean enabled;
    private Map<String, Object> params;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}