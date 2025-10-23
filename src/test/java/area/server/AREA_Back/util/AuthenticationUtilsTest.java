package area.server.AREA_Back.util;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.service.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour AuthenticationUtils
 * Type: Tests Unitaires
 * Description: Teste les méthodes utilitaires d'authentification
 */
@DisplayName("AuthenticationUtils - Tests Unitaires")
class AuthenticationUtilsTest {

    private User testUser;
    private CustomUserDetailsService.CustomUserPrincipal testPrincipal;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        // Créer un utilisateur de test
        testUserId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");
        testUser.setFirstname("Test");
        testUser.setLastname("User");
        testUser.setIsAdmin(false);

        // Créer un principal de test
        testPrincipal = new CustomUserDetailsService.CustomUserPrincipal(testUser);
    }

    @AfterEach
    void tearDown() {
        // Nettoyer le contexte de sécurité après chaque test
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Doit retourner null quand aucun utilisateur n'est authentifié")
    void shouldReturnNullWhenNoUserAuthenticated() {
        // Given
        SecurityContextHolder.clearContext();

        // When
        CustomUserDetailsService.CustomUserPrincipal result = AuthenticationUtils.getCurrentUser();

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Doit retourner l'utilisateur courant quand authentifié")
    void shouldReturnCurrentUserWhenAuthenticated() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
            testPrincipal, null, testPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        CustomUserDetailsService.CustomUserPrincipal result = AuthenticationUtils.getCurrentUser();

        // Then
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    @DisplayName("Doit retourner l'ID de l'utilisateur courant")
    void shouldReturnCurrentUserId() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
            testPrincipal, null, testPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        UUID result = AuthenticationUtils.getCurrentUserId();

        // Then
        assertNotNull(result);
        assertEquals(testUserId, result);
    }

    @Test
    @DisplayName("Doit retourner null pour l'ID quand utilisateur non authentifié")
    void shouldReturnNullUserIdWhenNotAuthenticated() {
        // Given
        SecurityContextHolder.clearContext();

        // When
        UUID result = AuthenticationUtils.getCurrentUserId();

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Doit retourner l'email de l'utilisateur courant")
    void shouldReturnCurrentUserEmail() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
            testPrincipal, null, testPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        String result = AuthenticationUtils.getCurrentUserEmail();

        // Then
        assertNotNull(result);
        assertEquals("test@example.com", result);
    }

    @Test
    @DisplayName("Doit retourner null pour l'email quand utilisateur non authentifié")
    void shouldReturnNullEmailWhenNotAuthenticated() {
        // Given
        SecurityContextHolder.clearContext();

        // When
        String result = AuthenticationUtils.getCurrentUserEmail();

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Doit retourner false quand l'utilisateur n'est pas admin")
    void shouldReturnFalseWhenUserIsNotAdmin() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
            testPrincipal, null, testPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        boolean result = AuthenticationUtils.isCurrentUserAdmin();

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit retourner true quand l'utilisateur est admin")
    void shouldReturnTrueWhenUserIsAdmin() {
        // Given
        testUser.setIsAdmin(true);
        CustomUserDetailsService.CustomUserPrincipal adminPrincipal = 
            new CustomUserDetailsService.CustomUserPrincipal(testUser);
        
        Authentication auth = new UsernamePasswordAuthenticationToken(
            adminPrincipal, null, adminPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        boolean result = AuthenticationUtils.isCurrentUserAdmin();

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit retourner false pour isAdmin quand utilisateur non authentifié")
    void shouldReturnFalseForIsAdminWhenNotAuthenticated() {
        // Given
        SecurityContextHolder.clearContext();

        // When
        boolean result = AuthenticationUtils.isCurrentUserAdmin();

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit retourner true pour isAuthenticated quand utilisateur authentifié")
    void shouldReturnTrueWhenAuthenticated() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
            testPrincipal, null, testPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        boolean result = AuthenticationUtils.isAuthenticated();

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Doit retourner false pour isAuthenticated quand utilisateur non authentifié")
    void shouldReturnFalseWhenNotAuthenticated() {
        // Given
        SecurityContextHolder.clearContext();

        // When
        boolean result = AuthenticationUtils.isAuthenticated();

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit retourner false pour isAuthenticated quand principal est une String")
    void shouldReturnFalseWhenPrincipalIsString() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "anonymousUser", null, Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        boolean result = AuthenticationUtils.isAuthenticated();

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit retourner l'entité User courante")
    void shouldReturnCurrentUserEntity() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
            testPrincipal, null, testPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        User result = AuthenticationUtils.getCurrentUserEntity();

        // Then
        assertNotNull(result);
        assertEquals(testUserId, result.getId());
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test", result.getFirstname());
        assertEquals("User", result.getLastname());
    }

    @Test
    @DisplayName("Doit retourner null pour l'entité User quand non authentifié")
    void shouldReturnNullUserEntityWhenNotAuthenticated() {
        // Given
        SecurityContextHolder.clearContext();

        // When
        User result = AuthenticationUtils.getCurrentUserEntity();

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Le constructeur doit être privé")
    void shouldHavePrivateConstructor() throws NoSuchMethodException {
        // When & Then
        var constructor = AuthenticationUtils.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()),
            "Le constructeur devrait être privé");
    }

    @Test
    @DisplayName("Doit gérer l'authentification null dans isAuthenticated")
    void shouldHandleNullAuthenticationInIsAuthenticated() {
        // Given
        SecurityContextHolder.getContext().setAuthentication(null);

        // When
        boolean result = AuthenticationUtils.isAuthenticated();

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Doit gérer l'authentification non valide")
    void shouldHandleInvalidAuthentication() {
        // Given
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "someString", null, Collections.emptyList()
        );
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        CustomUserDetailsService.CustomUserPrincipal result = AuthenticationUtils.getCurrentUser();

        // Then
        assertNull(result);
    }
}
