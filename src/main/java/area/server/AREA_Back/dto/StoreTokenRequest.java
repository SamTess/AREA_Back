package area.server.AREA_Back.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class StoreTokenRequest {
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresAt;
    private Map<String, Object> scopes;
}