package area.server.AREA_Back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAreaWithActionsRequest {

    @NotBlank(message = "Area name is required")
    private String name;

    private String description;

    private UUID userId;

    @Valid
    @NotEmpty(message = "At least one action is required")
    private List<AreaActionRequest> actions;

    @Valid
    private List<AreaReactionRequest> reactions;
}