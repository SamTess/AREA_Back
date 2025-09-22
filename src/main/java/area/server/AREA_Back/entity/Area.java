package area.server.AREA_Back.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "areas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Area {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Area name is required")
    @Size(min = 2, max = 100, message = "Area name must be between 2 and 100 characters")
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean enabled = true;

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Action service is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_service_id", nullable = false)
    private Service actionService;

    @NotBlank(message = "Action type is required")
    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "action_config", columnDefinition = "TEXT")
    private String actionConfig;

    @NotNull(message = "Reaction service is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reaction_service_id", nullable = false)
    private Service reactionService;

    @NotBlank(message = "Reaction type is required")
    @Column(name = "reaction_type", nullable = false)
    private String reactionType;

    @Column(name = "reaction_config", columnDefinition = "TEXT")
    private String reactionConfig;

    @Column(name = "last_triggered")
    private LocalDateTime lastTriggered;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}