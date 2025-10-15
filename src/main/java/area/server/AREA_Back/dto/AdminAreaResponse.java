package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAreaResponse {
    private UUID id;
    private String name;
    private String description;
    private Boolean enabled;
    private String user;
    private String userEmail;
    private UUID userId;
    private LocalDateTime lastRun;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
