package area.server.AREA_Back.dto;

import area.server.AREA_Back.entity.Service;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceAdminRequest {
    @NotBlank(message = "Service key is required")
    private String key;

    @NotBlank(message = "Service name is required")
    private String name;

    @NotNull(message = "Auth type is required")
    private Service.AuthType auth;

    private String docsUrl;

    private String iconLightUrl;

    private String iconDarkUrl;

    private Boolean isActive = true;
}
