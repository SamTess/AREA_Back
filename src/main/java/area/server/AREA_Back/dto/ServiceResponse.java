package area.server.AREA_Back.dto;

import area.server.AREA_Back.entity.Service;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponse {
    private UUID id;
    private String key;
    private String name;
    private Service.AuthType auth;
    private String docsUrl;
    private String iconLightUrl;
    private String iconDarkUrl;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}