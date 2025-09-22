package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaResponse {
    private Long id;
    private String name;
    private String description;
    private Boolean enabled;
    private Long userId;
    private String userUsername;
    private Long actionServiceId;
    private String actionServiceName;
    private String actionType;
    private String actionConfig;
    private Long reactionServiceId;
    private String reactionServiceName;
    private String reactionType;
    private String reactionConfig;
    private LocalDateTime lastTriggered;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}