package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTokenRequest {

    @NotBlank(message = "Service key is required")
    private String serviceKey;

    @NotBlank(message = "Access token is required")
    private String accessToken;

    private String refreshToken;

    private LocalDateTime expiresAt;

    private String remoteAccountId;

    private Map<String, Object> scopes;

    private String webhookSecret;
}