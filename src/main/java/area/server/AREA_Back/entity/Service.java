package area.server.AREA_Back.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "a_services", schema = "area")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank(message = "Service key is required")
    @Column(unique = true, nullable = false)
    private String key;

    @NotBlank(message = "Service name is required")
    @Column(nullable = false)
    private String name;

    @Column
    @Enumerated(EnumType.STRING)
    private AuthType auth = AuthType.OAUTH2;

    @Column(name = "docs_url")
    private String docsUrl;

    @Column(name = "icon_light_url")
    private String iconLightUrl;

    @Column(name = "icon_dark_url")
    private String iconDarkUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum AuthType {
        OAUTH2, APIKEY, NONE
    }
}