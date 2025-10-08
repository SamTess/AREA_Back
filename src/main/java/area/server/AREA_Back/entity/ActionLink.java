package area.server.AREA_Back.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "a_action_links", schema = "area")
@IdClass(ActionLinkId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionLink {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_action_instance_id", nullable = false)
    private ActionInstance sourceActionInstance;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_action_instance_id", nullable = false)
    private ActionInstance targetActionInstance;

    @NotNull(message = "Area is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> mapping = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> condition;

    @Column(name = "link_type", nullable = false)
    private String linkType = "chain"; // chain, conditional, parallel, sequential

    @Column(name = "\"order\"", nullable = false)
    private Integer order = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}