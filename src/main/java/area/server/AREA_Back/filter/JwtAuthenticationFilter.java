package area.server.AREA_Back.filter;

import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
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
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain
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
                }

            } catch (Exception e) {
                // In case of error, continue without authentication
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookies(final HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> ACCESS_TOKEN_COOKIE.equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    private boolean isPublicEndpoint(final String path) {
        return path.equals("/api/auth/login")
               || path.equals("/api/auth/register")
               || path.equals("/api/auth/forgot-password")
               || path.equals("/api/auth/reset-password")
               || path.equals("/api/auth/verify")
               || path.startsWith("/api/oauth/")
               || path.startsWith("/swagger-ui/")
               || path.startsWith("/v3/api-docs/")
               || path.equals("/swagger-ui.html")
               || path.startsWith("/api/about")
               || path.equals("/api/services/catalog")
               || path.equals("/api/services/catalog/enabled")
               || path.startsWith("/actuator/")
               || path.startsWith("/webjars/")
               || path.equals("/favicon.ico");
    }
}