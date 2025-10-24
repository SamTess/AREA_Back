package area.server.AREA_Back.dto;

import area.server.AREA_Back.validation.AtLeastOneIdentifier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AtLeastOneIdentifier(message = "Either email or username must be provided")
public class LocalLoginRequest {

    @Email(message = "Email should be valid")
    private String email;

    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}