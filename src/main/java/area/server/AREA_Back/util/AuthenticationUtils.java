package area.server.AREA_Back.util;

import area.server.AREA_Back.service.CustomUserDetailsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

public final class AuthenticationUtils {

    private AuthenticationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static CustomUserDetailsService.CustomUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
            && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof CustomUserDetailsService.CustomUserPrincipal) {

            return (CustomUserDetailsService.CustomUserPrincipal) authentication.getPrincipal();
        }

        return null;
    }

    public static UUID getCurrentUserId() {
        CustomUserDetailsService.CustomUserPrincipal user = getCurrentUser();
        if (user != null) {
            return user.getUserId();
        }
        return null;
    }

    public static String getCurrentUserEmail() {
        CustomUserDetailsService.CustomUserPrincipal user = getCurrentUser();
        if (user != null) {
            return user.getEmail();
        }
        return null;
    }

    public static boolean isCurrentUserAdmin() {
        CustomUserDetailsService.CustomUserPrincipal user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
               && authentication.isAuthenticated()
               && !(authentication.getPrincipal() instanceof String);
    }

    public static area.server.AREA_Back.entity.User getCurrentUserEntity() {
        CustomUserDetailsService.CustomUserPrincipal user = getCurrentUser();
        if (user != null) {
            return user.getUser();
        }
        return null;
    }
}