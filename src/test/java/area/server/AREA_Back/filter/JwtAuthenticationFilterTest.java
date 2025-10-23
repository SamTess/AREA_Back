package area.server.AREA_Back.filter;

import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.CustomUserDetailsService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour JwtAuthenticationFilter
 * Type: Tests Unitaires
 * Description: Teste le filtre d'authentification JWT
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter - Tests Unitaires")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String AUTH_TOKEN = "valid.jwt.token";
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Doit permettre l'accès aux endpoints publics sans token")
    void shouldAllowPublicEndpointsWithoutToken() throws ServletException, IOException {
        // Given
        String[] publicPaths = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/verify",
            "/api/oauth/google",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/api/about",
            "/api/services/catalog",
            "/api/services/catalog/enabled",
            "/actuator/health",
            "/webjars/swagger-ui/index.html",
            "/favicon.ico"
        };

        for (String path : publicPaths) {
            // When
            when(request.getRequestURI()).thenReturn(path);
            
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain, atLeastOnce()).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Test
    @DisplayName("Doit continuer le filtre quand aucun token n'est fourni")
    void shouldContinueFilterWhenNoTokenProvided() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        when(request.getCookies()).thenReturn(null);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(redisTokenService, never()).isAccessTokenValid(anyString());
    }

    @Test
    @DisplayName("Doit continuer le filtre quand le cookie authToken n'existe pas")
    void shouldContinueFilterWhenAuthTokenCookieDoesNotExist() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("otherCookie", "value")
        };
        when(request.getCookies()).thenReturn(cookies);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(redisTokenService, never()).isAccessTokenValid(anyString());
    }

    @Test
    @DisplayName("Doit continuer le filtre quand l'utilisateur est déjà authentifié")
    void shouldContinueFilterWhenUserAlreadyAuthenticated() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);

        // Simuler un utilisateur déjà authentifié
        Authentication existingAuth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(redisTokenService, never()).isAccessTokenValid(anyString());
    }

    @Test
    @DisplayName("Doit continuer le filtre quand le token n'est pas dans Redis")
    void shouldContinueFilterWhenTokenNotInRedis() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(false);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService, never()).extractUserIdFromAccessToken(anyString());
    }

    @Test
    @DisplayName("Doit authentifier l'utilisateur avec un token valide")
    void shouldAuthenticateUserWithValidToken() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(testUserId);
        when(jwtService.isAccessTokenValid(AUTH_TOKEN, testUserId)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(testUserId.toString())).thenReturn(userDetails);
        when(userDetails.getAuthorities()).thenReturn(null);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userDetails, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("Doit continuer le filtre quand le token JWT a expiré")
    void shouldContinueFilterWhenTokenExpired() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(AUTH_TOKEN))
            .thenThrow(new ExpiredJwtException(null, null, "Token expired"));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Doit continuer le filtre quand le token JWT est malformé")
    void shouldContinueFilterWhenTokenMalformed() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(AUTH_TOKEN))
            .thenThrow(new MalformedJwtException("Malformed token"));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Doit continuer le filtre quand la signature JWT est invalide")
    void shouldContinueFilterWhenSignatureInvalid() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(AUTH_TOKEN))
            .thenThrow(new SignatureException("Invalid signature"));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Doit continuer le filtre quand le format du token est invalide")
    void shouldContinueFilterWhenTokenFormatInvalid() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(AUTH_TOKEN))
            .thenThrow(new IllegalArgumentException("Invalid token format"));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Doit continuer le filtre en cas d'exception inattendue")
    void shouldContinueFilterOnUnexpectedException() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(AUTH_TOKEN))
            .thenThrow(new RuntimeException("Unexpected error"));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Doit continuer le filtre quand la validation du token échoue")
    void shouldContinueFilterWhenTokenValidationFails() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("authToken", AUTH_TOKEN)
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(testUserId);
        when(jwtService.isAccessTokenValid(AUTH_TOKEN, testUserId)).thenReturn(false);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("Doit extraire le token du cookie authToken")
    void shouldExtractTokenFromAuthTokenCookie() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/user/profile");
        Cookie[] cookies = new Cookie[]{
            new Cookie("otherCookie", "otherValue"),
            new Cookie("authToken", AUTH_TOKEN),
            new Cookie("anotherCookie", "anotherValue")
        };
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(AUTH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(testUserId);
        when(jwtService.isAccessTokenValid(AUTH_TOKEN, testUserId)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(testUserId.toString())).thenReturn(userDetails);
        when(userDetails.getAuthorities()).thenReturn(null);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(redisTokenService).isAccessTokenValid(AUTH_TOKEN);
        verify(jwtService).extractUserIdFromAccessToken(AUTH_TOKEN);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
