package area.server.AREA_Back.dto;

import area.server.AREA_Back.entity.Service;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceRequest {

    @NotBlank(message = "Service key is required")
    private String key;

    @NotBlank(message = "Service name is required")
    private String name;

    private Service.AuthType auth = Service.AuthType.OAUTH2;

    private String docsUrl;
    private String iconLightUrl;
    private String iconDarkUrl;
}