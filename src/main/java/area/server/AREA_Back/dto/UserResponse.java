package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String username;
    private String firstname;
    private String lastname;
    private Boolean isActive;
    private Boolean isAdmin;
    private Boolean isVerified;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private String avatarUrl;
}
