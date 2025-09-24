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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "a_events", schema = "area")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Action instance is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_instance_id", nullable = false)
    private ActionInstance actionInstance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @CreationTimestamp
    @Column(name = "emitted_at", nullable = false, updatable = false)
    private LocalDateTime emittedAt;

    @Column(name = "source_event_id")
    private String sourceEventId;
}