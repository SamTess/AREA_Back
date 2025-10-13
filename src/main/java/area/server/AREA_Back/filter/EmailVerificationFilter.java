package area.server.AREA_Back.filter;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that restricts access for non-verified users.
 * Non-verified users can only access authentication and OAuth endpoints.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final UserLocalIdentityRepository userLocalIdentityRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String userIdStr = authentication.getName();
            UUID userId = UUID.fromString(userIdStr);

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("Authenticated user not found in database: {}", userId);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            UserLocalIdentity localIdentity = userLocalIdentityRepository
                .findByUserId(userId)
                .orElse(null);

            if (localIdentity == null) {
                log.warn("No local identity found for user: {}", userId);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            if (Boolean.TRUE.equals(localIdentity.getIsEmailVerified())) {
                filterChain.doFilter(request, response);
                return;
            }

            String requestPath = request.getRequestURI();
            if (isAllowedForNonVerifiedUsers(requestPath)) {
                filterChain.doFilter(request, response);
                return;
            }

            log.warn("Non-verified user {} attempted to access restricted endpoint: {}", userId, requestPath);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Email verification required\", \"message\": \"Please verify your email address to access this feature\"}");

        } catch (Exception e) {
            log.error("Error in email verification filter", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Internal server error\"}");
        }
    }

    /**
     * Check if the endpoint is allowed for non-verified users
     */
    private boolean isAllowedForNonVerifiedUsers(String path) {
        if (path.startsWith("/api/auth/")) {
            return true;
        }

        if (path.startsWith("/api/oauth/")) {
            return true;
        }

        return path.startsWith("/swagger-ui/")
               || path.startsWith("/v3/api-docs/")
               || path.equals("/swagger-ui.html")
               || path.startsWith("/actuator/")
               || path.startsWith("/webjars/")
               || path.equals("/favicon.ico");
    }
}