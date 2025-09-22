package area.server.AREA_Back.dto;

import area.server.AREA_Back.entity.Service;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceRequest {

    @NotBlank(message = "Service name is required")
    @Size(min = 2, max = 100, message = "Service name must be between 2 and 100 characters")
    private String name;

    private String description;

    @NotBlank(message = "Service icon URL is required")
    private String iconUrl;

    private String apiEndpoint;

    private Service.AuthType authType = Service.AuthType.OAUTH2;
}