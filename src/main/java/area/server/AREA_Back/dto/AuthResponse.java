package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String message;
    private UserResponse user;
    private String token;
    private String refreshToken;

    public AuthResponse(String message, UserResponse user) {
        this.message = message;
        this.user = user;
    }
}