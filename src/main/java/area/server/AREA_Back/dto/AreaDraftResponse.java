package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaDraftResponse {

    private String draftId;
    private String name;
    private String description;
    private UUID userId;
    private List<AreaActionRequest> actions;
    private List<AreaReactionRequest> reactions;
    private List<AreaDraftRequest.ConnectionRequest> connections;
    private String layoutMode;
    private LocalDateTime savedAt;
    private Long ttlSeconds;
}
