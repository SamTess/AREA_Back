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
public class ActionDefinitionResponse {
    private UUID id;
    private UUID serviceId;
    private String serviceKey;
    private String serviceName;
    private String key;
    private String name;
    private String description;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private String docsUrl;
    private Boolean isEventCapable;
    private Boolean isExecutable;
    private Integer version;
    private Integer defaultPollIntervalSeconds;
    private Map<String, Object> throttlePolicy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}