package area.server.AREA_Back.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "services")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Service name is required")
    @Size(min = 2, max = 100, message = "Service name must be between 2 and 100 characters")
    @Column(unique = true, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "Service icon URL is required")
    @Column(name = "icon_url")
    private String iconUrl;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "api_endpoint")
    private String apiEndpoint;

    @Column(name = "auth_type")
    @Enumerated(EnumType.STRING)
    private AuthType authType = AuthType.OAUTH2;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "actionService", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Area> actionAreas;

    @OneToMany(mappedBy = "reactionService", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Area> reactionAreas;

    public enum AuthType {
        OAUTH2, API_KEY, BASIC_AUTH, NONE
    }
}