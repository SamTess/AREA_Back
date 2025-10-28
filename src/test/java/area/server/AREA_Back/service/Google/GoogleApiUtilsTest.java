package area.server.AREA_Back.service.Google;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Area.Services.Google.GoogleApiUtils;
import area.server.AREA_Back.service.Auth.ServiceAccountService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleApiUtils
 * Tests utility methods for Google API operations
 */
@ExtendWith(MockitoExtension.class)
class GoogleApiUtilsTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private ServiceAccountService serviceAccountService;

    private GoogleApiUtils googleApiUtils;

    @BeforeEach
    void setUp() {
        googleApiUtils = new GoogleApiUtils(
            userOAuthIdentityRepository,
            userRepository,
            tokenEncryptionService,
            serviceAccountService
        );
    }

    @Test
    void testGetGoogleTokenFromServiceAccount() {
        // Given
        UUID userId = UUID.randomUUID();
        String expectedToken = "service-account-token";

        when(serviceAccountService.getAccessToken(userId, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.of(expectedToken));

        // When
        String result = googleApiUtils.getGoogleToken(userId);

        // Then
        assertEquals(expectedToken, result);
        verify(serviceAccountService).getAccessToken(userId, GoogleApiUtils.GOOGLE_PROVIDER_KEY);
        verifyNoInteractions(userRepository, userOAuthIdentityRepository);
    }

    @Test
    void testGetGoogleTokenFromOAuthIdentity() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        String encryptedToken = "encrypted-token";
        String decryptedToken = "decrypted-token";

        oauthIdentity.setAccessTokenEnc(encryptedToken);

        when(serviceAccountService.getAccessToken(userId, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken(encryptedToken)).thenReturn(decryptedToken);

        // When
        String result = googleApiUtils.getGoogleToken(userId);

        // Then
        assertEquals(decryptedToken, result);
        verify(tokenEncryptionService).decryptToken(encryptedToken);
    }

    @Test
    void testGetGoogleTokenUserNotFound() {
        // Given
        UUID userId = UUID.randomUUID();

        when(serviceAccountService.getAccessToken(userId, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        String result = googleApiUtils.getGoogleToken(userId);

        // Then
        assertNull(result);
    }

    @Test
    void testGetGoogleTokenNoOAuthIdentity() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();

        when(serviceAccountService.getAccessToken(userId, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.empty());

        // When
        String result = googleApiUtils.getGoogleToken(userId);

        // Then
        assertNull(result);
    }

    @Test
    void testGetGoogleTokenNullOrEmptyToken() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setAccessTokenEnc(null);

        when(serviceAccountService.getAccessToken(userId, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.of(oauthIdentity));

        // When
        String result = googleApiUtils.getGoogleToken(userId);

        // Then
        assertNull(result);
    }

    @Test
    void testGetGoogleTokenDecryptionError() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        String encryptedToken = "encrypted-token";

        oauthIdentity.setAccessTokenEnc(encryptedToken);

        when(serviceAccountService.getAccessToken(userId, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userOAuthIdentityRepository.findByUserAndProvider(user, GoogleApiUtils.GOOGLE_PROVIDER_KEY))
            .thenReturn(Optional.of(oauthIdentity));
        when(tokenEncryptionService.decryptToken(encryptedToken))
            .thenThrow(new RuntimeException("Decryption failed"));

        // When
        String result = googleApiUtils.getGoogleToken(userId);

        // Then
        assertNull(result);
    }

    @Test
    void testCreateGoogleHeaders() {
        // Given
        String token = "test-token";

        // When
        HttpHeaders result = googleApiUtils.createGoogleHeaders(token);

        // Then
        assertNotNull(result);
        assertEquals(MediaType.APPLICATION_JSON, result.getContentType());
        assertTrue(result.containsKey(HttpHeaders.AUTHORIZATION));
        assertEquals("Bearer " + token, result.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testGetRequiredParamSuccess() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");

        // When
        String result = googleApiUtils.getRequiredParam(params, "key", String.class);

        // Then
        assertEquals("value", result);
    }

    @Test
    void testGetRequiredParamMissing() {
        // Given
        Map<String, Object> params = new HashMap<>();

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            googleApiUtils.getRequiredParam(params, "missing", String.class);
        });

        assertTrue(exception.getMessage().contains("Required parameter missing: missing"));
    }

    @Test
    void testGetOptionalParamPresent() {
        // Given
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");

        // When
        String result = googleApiUtils.getOptionalParam(params, "key", String.class, "default");

        // Then
        assertEquals("value", result);
    }

    @Test
    void testGetOptionalParamMissing() {
        // Given
        Map<String, Object> params = new HashMap<>();

        // When
        String result = googleApiUtils.getOptionalParam(params, "missing", String.class, "default");

        // Then
        assertEquals("default", result);
    }

    @Test
    void testMapToJsonSimple() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("name", "John");
        map.put("age", 30);

        // When
        String result = googleApiUtils.mapToJson(map);

        // Then
        assertTrue(result.contains("\"name\":\"John\""));
        assertTrue(result.contains("\"age\":30"));
        assertTrue(result.startsWith("{"));
        assertTrue(result.endsWith("}"));
    }

    @Test
    void testMapToJsonWithNull() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("name", "John");
        map.put("value", null);

        // When
        String result = googleApiUtils.mapToJson(map);

        // Then
        assertTrue(result.contains("\"name\":\"John\""));
        assertTrue(result.contains("\"value\":null"));
    }

    @Test
    void testMapToJsonNested() {
        // Given
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("city", "Paris");

        Map<String, Object> map = new HashMap<>();
        map.put("address", innerMap);

        // When
        String result = googleApiUtils.mapToJson(map);

        // Then
        assertTrue(result.contains("\"address\":{"));
        assertTrue(result.contains("\"city\":\"Paris\""));
    }

    @Test
    void testListToJsonSimple() {
        // Given
        List<Object> list = Arrays.asList("apple", "banana", "cherry");

        // When
        String result = googleApiUtils.listToJson(list);

        // Then
        assertEquals("[\"apple\",\"banana\",\"cherry\"]", result);
    }

    @Test
    void testListToJsonWithNumbers() {
        // Given
        List<Object> list = Arrays.asList(1, 2, 3);

        // When
        String result = googleApiUtils.listToJson(list);

        // Then
        assertEquals("[1,2,3]", result);
    }

    @Test
    void testListToJsonWithMixed() {
        // Given
        List<Object> list = Arrays.asList("text", 42, true, null);

        // When
        String result = googleApiUtils.listToJson(list);

        // Then
        assertEquals("[\"text\",42,true,null]", result);
    }

    @Test
    void testEscapeJsonBasic() {
        // Given
        String input = "Hello World";

        // When
        String result = googleApiUtils.escapeJson(input);

        // Then
        assertEquals("Hello World", result);
    }

    @Test
    void testEscapeJsonWithSpecialChars() {
        // Given
        String input = "Line1\nLine2\rTab\there\"quote\"back\\slash";

        // When
        String result = googleApiUtils.escapeJson(input);

        // Then
        assertEquals("Line1\\nLine2\\rTab\\there\\\"quote\\\"back\\\\slash", result);
    }

    @Test
    void testEscapeJsonNull() {
        // Given
        String input = null;

        // When
        String result = googleApiUtils.escapeJson(input);

        // Then
        assertEquals("", result);
    }

    @Test
    void testMapToJsonWithBoolean() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("active", true);
        map.put("deleted", false);

        // When
        String result = googleApiUtils.mapToJson(map);

        // Then
        assertTrue(result.contains("\"active\":true"));
        assertTrue(result.contains("\"deleted\":false"));
    }

    @Test
    void testMapToJsonWithList() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("items", Arrays.asList("a", "b", "c"));

        // When
        String result = googleApiUtils.mapToJson(map);

        // Then
        assertTrue(result.contains("\"items\":[\"a\",\"b\",\"c\"]"));
    }

    @Test
    void testListToJsonEmpty() {
        // Given
        List<Object> list = Collections.emptyList();

        // When
        String result = googleApiUtils.listToJson(list);

        // Then
        assertEquals("[]", result);
    }

    @Test
    void testMapToJsonEmpty() {
        // Given
        Map<String, Object> map = new HashMap<>();

        // When
        String result = googleApiUtils.mapToJson(map);

        // Then
        assertEquals("{}", result);
    }
}
