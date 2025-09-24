package area.server.AREA_Back.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "a_action_definitions", schema = "area",
       uniqueConstraints = @UniqueConstraint(columnNames = {"service_id", "key", "version"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Service is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @NotBlank(message = "Key is required")
    @Column(nullable = false)
    private String key;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> inputSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> outputSchema;

    @Column(name = "docs_url")
    private String docsUrl;

    @Column(name = "is_event_capable", nullable = false)
    private Boolean isEventCapable = false;

    @Column(name = "is_executable", nullable = false)
    private Boolean isExecutable = false;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "default_poll_interval_seconds")
    private Integer defaultPollIntervalSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "throttle_policy", columnDefinition = "jsonb")
    private Map<String, Object> throttlePolicy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}