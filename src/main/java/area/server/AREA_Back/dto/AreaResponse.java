package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}