package area.server.AREA_Back.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaCollaboratorId implements Serializable {
    private UUID area;
    private UUID user;
}