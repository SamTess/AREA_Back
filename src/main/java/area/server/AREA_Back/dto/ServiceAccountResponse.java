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
public class ServiceAccountResponse {

    private UUID id;
    private String serviceKey;
    private String serviceName;
    private String remoteAccountId;
    private boolean hasAccessToken;
    private boolean hasRefreshToken;
    private LocalDateTime expiresAt;
    private boolean expired;
    private Map<String, Object> scopes;
    private Integer tokenVersion;
    private LocalDateTime lastRefreshAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}