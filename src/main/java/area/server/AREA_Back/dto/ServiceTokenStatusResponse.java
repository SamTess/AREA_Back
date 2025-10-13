package area.server.AREA_Back.dto;

import lombok.Data;

@Data
public class ServiceTokenStatusResponse {
    private boolean hasValidToken;
    private String serviceName;
}