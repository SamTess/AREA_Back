package area.server.AREA_Back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAreaRequest {

    @NotBlank(message = "Area name is required")
    @Size(min = 2, max = 100, message = "Area name must be between 2 and 100 characters")
    private String name;

    private String description;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Action service ID is required")
    private Long actionServiceId;

    @NotBlank(message = "Action type is required")
    private String actionType;

    private String actionConfig;

    @NotNull(message = "Reaction service ID is required")
    private Long reactionServiceId;

    @NotBlank(message = "Reaction type is required")
    private String reactionType;

    private String reactionConfig;
}