package area.server.AREA_Back.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour AuthTokenConstants
 * Type: Tests Unitaires
 * Description: Teste les constantes d'authentification et leur accessibilité
 */
@DisplayName("AuthTokenConstants - Tests Unitaires")
class AuthTokenConstantsTest {

    @Test
    @DisplayName("Doit avoir les bonnes valeurs pour les noms de cookies")
    void shouldHaveCorrectCookieNames() {
        assertEquals("authToken", AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME);
        assertEquals("refreshToken", AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME);
    }

    @Test
    @DisplayName("Doit avoir les bons préfixes Redis pour les tokens")
    void shouldHaveCorrectRedisTokenPrefixes() {
        assertEquals("access:", AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX);
        assertEquals("refresh:", AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX);
    }

    @Test
    @DisplayName("Doit avoir les bons préfixes Redis pour les JTI mappings")
    void shouldHaveCorrectRedisJtiPrefixes() {
        assertEquals("access:jti:", AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX);
        assertEquals("refresh:jti:", AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX);
    }

    @Test
    @DisplayName("Doit avoir le bon préfixe Redis pour les tokens utilisateur")
    void shouldHaveCorrectRedisUserTokensPrefix() {
        assertEquals("user:tokens:", AuthTokenConstants.REDIS_USER_TOKENS_PREFIX);
    }

    @Test
    @DisplayName("Le constructeur doit être privé")
    void shouldHavePrivateConstructor() throws NoSuchMethodException {
        Constructor<AuthTokenConstants> constructor = 
            AuthTokenConstants.class.getDeclaredConstructor();
        
        assertTrue(Modifier.isPrivate(constructor.getModifiers()),
            "Constructor should be private");
    }

    @Test
    @DisplayName("Le constructeur doit être privé et ne devrait pas être utilisé")
    void shouldHavePrivateConstructorNotUsed() throws NoSuchMethodException {
        // Given
        Constructor<AuthTokenConstants> constructor = 
            AuthTokenConstants.class.getDeclaredConstructor();
        
        // Then
        assertTrue(Modifier.isPrivate(constructor.getModifiers()),
            "Constructor should be private to prevent instantiation");
    }

    @Test
    @DisplayName("Les constantes ne doivent pas être null")
    void shouldNotHaveNullConstants() {
        assertNotNull(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME);
        assertNotNull(AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME);
        assertNotNull(AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX);
        assertNotNull(AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX);
        assertNotNull(AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX);
        assertNotNull(AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX);
        assertNotNull(AuthTokenConstants.REDIS_USER_TOKENS_PREFIX);
    }

    @Test
    @DisplayName("Les constantes ne doivent pas être vides")
    void shouldNotHaveEmptyConstants() {
        assertFalse(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME.isEmpty());
        assertFalse(AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME.isEmpty());
        assertFalse(AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX.isEmpty());
        assertFalse(AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX.isEmpty());
        assertFalse(AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX.isEmpty());
        assertFalse(AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX.isEmpty());
        assertFalse(AuthTokenConstants.REDIS_USER_TOKENS_PREFIX.isEmpty());
    }

    @Test
    @DisplayName("Les préfixes Redis doivent se terminer par deux-points")
    void shouldHaveRedisPrefixesEndingWithColon() {
        assertTrue(AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX.endsWith(":"));
        assertTrue(AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX.endsWith(":"));
        assertTrue(AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX.endsWith(":"));
        assertTrue(AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX.endsWith(":"));
        assertTrue(AuthTokenConstants.REDIS_USER_TOKENS_PREFIX.endsWith(":"));
    }

    @Test
    @DisplayName("Les noms de cookies doivent être différents")
    void shouldHaveDifferentCookieNames() {
        assertNotEquals(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME,
                       AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME);
    }

    @Test
    @DisplayName("Les préfixes Redis access et refresh doivent être différents")
    void shouldHaveDifferentRedisTokenPrefixes() {
        assertNotEquals(AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX,
                       AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX);
    }

    @Test
    @DisplayName("Les préfixes Redis JTI doivent être différents")
    void shouldHaveDifferentRedisJtiPrefixes() {
        assertNotEquals(AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX,
                       AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX);
    }

    @Test
    @DisplayName("La classe doit être finale")
    void shouldBeFinalClass() {
        assertTrue(Modifier.isFinal(AuthTokenConstants.class.getModifiers()),
            "Constants class should be final");
    }

    @Test
    @DisplayName("Les constantes doivent être publiques et finales")
    void shouldHavePublicFinalConstants() throws NoSuchFieldException {
        var accessTokenField = AuthTokenConstants.class.getField("ACCESS_TOKEN_COOKIE_NAME");
        assertTrue(Modifier.isPublic(accessTokenField.getModifiers()));
        assertTrue(Modifier.isStatic(accessTokenField.getModifiers()));
        assertTrue(Modifier.isFinal(accessTokenField.getModifiers()));
    }

    @Test
    @DisplayName("Les constantes doivent être des chaînes de caractères")
    void shouldHaveStringConstants() throws NoSuchFieldException {
        assertEquals(String.class, 
            AuthTokenConstants.class.getField("ACCESS_TOKEN_COOKIE_NAME").getType());
        assertEquals(String.class, 
            AuthTokenConstants.class.getField("REFRESH_TOKEN_COOKIE_NAME").getType());
        assertEquals(String.class, 
            AuthTokenConstants.class.getField("REDIS_ACCESS_TOKEN_PREFIX").getType());
    }
}
