package area.server.AREA_Back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAreaRequest {

    @NotBlank(message = "Area name is required")
    private String name;

    private String description;

    @NotNull(message = "User ID is required")
    private UUID userId;
}