package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLinkErrorResponse {
    private String error;
    private String message;
    private String suggestion;

    public static OAuthLinkErrorResponse accountAlreadyLinked(String provider) {
        return new OAuthLinkErrorResponse(
            "ACCOUNT_ALREADY_LINKED",
            "This " + provider + " account is already linked to another user",
            "Please use a different " + provider + " account or disconnect it from the other user first"
        );
    }

    public static OAuthLinkErrorResponse emailRequired(String provider) {
        return new OAuthLinkErrorResponse(
            "EMAIL_REQUIRED",
            "Email is required from your " + provider + " account",
            "Please make sure your " + provider + " account has a public email address"
        );
    }

    public static OAuthLinkErrorResponse internalError() {
        return new OAuthLinkErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred while linking your account",
            "Please try again later or contact support"
        );
    }
}