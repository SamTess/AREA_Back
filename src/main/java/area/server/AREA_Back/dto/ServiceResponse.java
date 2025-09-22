package area.server.AREA_Back.dto;

import area.server.AREA_Back.entity.Service;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponse {
    private Long id;
    private String name;
    private String description;
    private String iconUrl;
    private Boolean enabled;
    private String apiEndpoint;
    private Service.AuthType authType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}