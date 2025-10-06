package area.server.AREA_Back.util;

import area.server.AREA_Back.service.CustomUserDetailsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility to easily retrieve authenticated user information
 * from any controller or service
 */
@Component
public final class AuthenticationUtils {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private AuthenticationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Retrieves the currently authenticated user
     * @return The CustomUserPrincipal object or null if not authenticated
     */
    public static CustomUserDetailsService.CustomUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
            && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof CustomUserDetailsService.CustomUserPrincipal) {

            return (CustomUserDetailsService.CustomUserPrincipal) authentication.getPrincipal();
        }

        return null;
    }

    /**
     * Retrieves the ID of the currently authenticated user
     * @return The user's UUID or null if not authenticated
     */
    public static UUID getCurrentUserId() {
        CustomUserDetailsService.CustomUserPrincipal user = getCurrentUser();
        if (user != null) {
            return user.getUserId();
        }
        return null;
    }

    /**
     * Retrieves the email of the currently authenticated user
     * @return The user's email or null if not authenticated
     */
    public static String getCurrentUserEmail() {
        CustomUserDetailsService.CustomUserPrincipal user = getCurrentUser();
        if (user != null) {
            return user.getEmail();
        }
        return null;
    }

    /**
     * Checks if the current user is an administrator
     * @return true if admin, false otherwise
     */
    public static boolean isCurrentUserAdmin() {
        CustomUserDetailsService.CustomUserPrincipal user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    /**
     * Checks if a user is currently authenticated
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
               && authentication.isAuthenticated()
               && !(authentication.getPrincipal() instanceof String); // Avoids "anonymousUser"
    }

    /**
     * Retrieves the full User entity of the authenticated user
     * @return The User entity or null if not authenticated
     */
    public static area.server.AREA_Back.entity.User getCurrentUserEntity() {
        CustomUserDetailsService.CustomUserPrincipal user = getCurrentUser();
        if (user != null) {
            return user.getUser();
        }
        return null;
    }
}