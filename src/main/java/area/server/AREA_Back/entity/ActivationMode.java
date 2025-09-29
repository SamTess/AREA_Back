package area.server.AREA_Back.entity;

import area.server.AREA_Back.entity.enums.ActivationModeType;
import area.server.AREA_Back.entity.enums.DedupStrategy;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "a_activation_modes", schema = "area")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivationMode {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Action instance is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_instance_id", nullable = false)
    private ActionInstance actionInstance;

    @NotNull(message = "Type is required")
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ActivationModeType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DedupStrategy dedup = DedupStrategy.NONE;

    @Column(name = "max_concurrency")
    private Integer maxConcurrency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rate_limit", columnDefinition = "jsonb")
    private Map<String, Object> rateLimit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}