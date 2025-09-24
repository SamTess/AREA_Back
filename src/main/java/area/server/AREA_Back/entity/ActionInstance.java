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
@Table(name = "a_action_instances", schema = "area")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Area is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @NotNull(message = "Action definition is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_def_id", nullable = false)
    private ActionDefinition actionDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_account_id")
    private ServiceAccount serviceAccount;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> params;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}