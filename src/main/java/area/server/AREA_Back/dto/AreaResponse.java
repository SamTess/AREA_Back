package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaResponse {
    private UUID id;
    private String name;
    private String description;
    private Boolean enabled;
    private UUID userId;
    private String userEmail;
    private List<Map<String, Object>> actions;
    private List<Map<String, Object>> reactions;
    private List<ActionLinkResponse> links;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}