package area.server.AREA_Back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConnectionStatus {
    private String serviceKey;
    private String serviceName;
    private String iconUrl;
    @JsonProperty("isConnected")
    private boolean isConnected;
    private String connectionType;
    private String userEmail;
    private String userName;
    private String avatarUrl;
    private String providerUserId;
    @JsonProperty("canDisconnect")
    private boolean canDisconnect;
    @JsonProperty("isPrimaryAuth")
    private boolean isPrimaryAuth;
}