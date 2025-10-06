package area.server.AREA_Back.filter;

import area.server.AREA_Back.service.JwtService;
import area.server.AREA_Back.service.RedisTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * JWT filter for authentication based on HttpOnly cookies.
 * This filter intercepts all requests and checks for the presence
 * of a valid authentication token in the HttpOnly cookies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RedisTokenService redisTokenService;
    private final UserDetailsService userDetailsService;

    private static final String ACCESS_TOKEN_COOKIE = "authToken";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        if (isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authToken = extractTokenFromCookies(request);

        if (authToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (!redisTokenService.isAccessTokenValid(authToken)) {
                    log.debug("Token not found in Redis or expired");
                    filterChain.doFilter(request, response);
                    return;
                }

                UUID userId = jwtService.extractUserIdFromAccessToken(authToken);

                if (jwtService.isAccessTokenValid(authToken, userId)) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(userId.toString());

                    UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                        );

                    authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                    log.debug("Successfully authenticated user with ID: {}", userId);
                } else {
                    log.debug("JWT token validation failed");
                }

            } catch (Exception e) {
                log.debug("JWT token processing failed: {}", e.getMessage());
                // In case of error, continue without authentication
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the authentication token from HttpOnly cookies.
     */
    private String extractTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> ACCESS_TOKEN_COOKIE.equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    /**
     * Checks if the endpoint is public and does not require authentication.
     */
    private boolean isPublicEndpoint(String path) {
        return path.equals("/api/auth/login") ||
               path.equals("/api/auth/register") ||
               path.equals("/api/auth/logout") ||
               path.equals("/api/auth/refresh") ||
               path.startsWith("/api/debug/") ||
               path.startsWith("/api/oauth/") ||
               path.equals("/api/services/catalog") ||
               path.equals("/api/services/catalog/enabled") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs/") ||
               path.equals("/swagger-ui.html") ||
               path.startsWith("/api/about") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/favicon.ico");
    }
}