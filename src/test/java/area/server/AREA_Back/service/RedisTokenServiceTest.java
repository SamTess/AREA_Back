package area.server.AREA_Back.service;package area.server.AREA_Back.service;package area.server.AREA_Back.service;package area.server.AREA_Back.service;package area.server.AREA_Back.service;



import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;import org.junit.jupiter.api.BeforeEach;

import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;import org.junit.jupiter.api.Test;

import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.data.redis.core.ValueOperations;import org.junit.jupiter.api.extension.ExtendWith;import org.junit.jupiter.api.BeforeEach;



import java.time.Duration;import org.mockito.Mock;

import java.util.UUID;

import org.mockito.junit.jupiter.MockitoExtension;import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertNull;

import static org.junit.jupiter.api.Assertions.assertTrue;import org.springframework.data.redis.core.ValueOperations;import org.junit.jupiter.api.extension.ExtendWith;import org.junit.jupiter.api.BeforeEach;import org.junit.jupiter.api.BeforeEach;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.verify;import java.time.Duration;import org.mockito.Mock;

import static org.mockito.Mockito.when;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)

class RedisTokenServiceTest {import org.mockito.junit.jupiter.MockitoExtension;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.Test;



    @Mockimport static org.junit.jupiter.api.Assertions.assertEquals;

    private RedisTemplate<String, Object> redisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;import org.springframework.data.redis.core.RedisTemplate;

    @Mock

    private ValueOperations<String, Object> valueOperations;import static org.junit.jupiter.api.Assertions.assertNull;



    @Mockimport static org.junit.jupiter.api.Assertions.assertTrue;import org.springframework.data.redis.core.ValueOperations;import org.junit.jupiter.api.extension.ExtendWith;import org.junit.jupiter.api.extension.ExtendWith;

    private JwtService jwtService;

import static org.mockito.ArgumentMatchers.eq;

    private RedisTokenService redisTokenService;

    private UUID testUserId;import static org.mockito.Mockito.verify;

    private String testAccessToken;

    private String testRefreshToken;import static org.mockito.Mockito.when;



    @BeforeEachimport java.time.Duration;import org.mockito.Mock;import org.mockito.Mock;

    void setUp() {

        redisTokenService = new RedisTokenService(redisTemplate, jwtService);@ExtendWith(MockitoExtension.class)

        testUserId = UUID.randomUUID();

        testAccessToken = "test.access.token";class RedisTokenServiceTest {import java.util.UUID;

        testRefreshToken = "test.refresh.token";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    }

    @Mockimport org.mockito.junit.jupiter.MockitoExtension;import org.mockito.junit.jupiter.MockitoExtension;

    @Test

    void testStoreAccessToken() {    private RedisTemplate<String, Object> redisTemplate;

        // When

        redisTokenService.storeAccessToken(testAccessToken, testUserId);import static org.junit.jupiter.api.Assertions.assertEquals;



        // Then    @Mock

        verify(valueOperations).set(

                eq("access:" + testAccessToken),    private ValueOperations<String, Object> valueOperations;import static org.junit.jupiter.api.Assertions.assertFalse;import org.springframework.data.redis.core.RedisTemplate;import org.springframework.data.redis.core.RedisTemplate;

                eq(testUserId.toString()),

                eq(Duration.ofMillis(900000L))

        );

    }    @Mockimport static org.junit.jupiter.api.Assertions.assertNull;



    @Test    private JwtService jwtService;

    void testStoreRefreshToken() {

        // Whenimport static org.junit.jupiter.api.Assertions.assertTrue;import org.springframework.data.redis.core.ValueOperations;import org.springframework.data.redis.core.ValueOperations;

        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

    private RedisTokenService redisTokenService;

        // Then

        verify(valueOperations).set(    private UUID testUserId;import static org.mockito.ArgumentMatchers.any;

                eq("refresh:" + testUserId.toString()),

                eq(testRefreshToken),    private String testAccessToken;

                eq(Duration.ofMillis(86400000L))

        );    private String testRefreshToken;import static org.mockito.ArgumentMatchers.anyString;

    }



    @Test

    void testIsAccessTokenValid() {    @BeforeEachimport static org.mockito.ArgumentMatchers.eq;

        // Given

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());    void setUp() {



        // When        redisTokenService = new RedisTokenService(redisTemplate, jwtService);import static org.mockito.Mockito.verify;import java.time.Duration;import java.time.Duration;

        boolean result = redisTokenService.isAccessTokenValid(testAccessToken);

        testUserId = UUID.randomUUID();

        // Then

        assertTrue(result);        testAccessToken = "test.access.token";import static org.mockito.Mockito.when;

    }

        testRefreshToken = "test.refresh.token";

    @Test

    void testIsAccessTokenValidReturnsFalseWhenNull() {import java.util.UUID;import java.util.UUID;

        // Given

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);        when(redisTemplate.opsForValue()).thenReturn(valueOperations);



        // When    }@ExtendWith(MockitoExtension.class)

        boolean result = redisTokenService.isAccessTokenValid(testAccessToken);



        // Then

        assertFalse(result);    @Testclass RedisTokenServiceTest {

    }

    void testStoreAccessToken() {

    @Test

    void testGetUserIdFromAccessToken() {        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        // Given

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());



        // When        redisTokenService.storeAccessToken(testAccessToken, testUserId);    @Mockimport static org.junit.jupiter.api.Assertions.assertEquals;import static org.junit.jupiter.api.Assertions.assertEquals;

        UUID result = redisTokenService.getUserIdFromAccessToken(testAccessToken);



        // Then

        assertEquals(testUserId, result);        verify(valueOperations).set(    private RedisTemplate<String, Object> redisTemplate;

    }

                eq("access:" + testAccessToken),

    @Test

    void testGetUserIdFromAccessTokenReturnsNullWhenNotFound() {                eq(testUserId.toString()),import static org.junit.jupiter.api.Assertions.assertFalse;import static org.junit.jupiter.api.Assertions.assertFalse;

        // Given

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);                eq(Duration.ofMillis(900000L))



        // When        );    @Mock

        UUID result = redisTokenService.getUserIdFromAccessToken(testAccessToken);

    }

        // Then

        assertNull(result);    private ValueOperations<String, Object> valueOperations;import static org.junit.jupiter.api.Assertions.assertNull;import static org.junit.jupiter.api.Assertions.assertNull;

    }

    @Test

    @Test

    void testGetRefreshToken() {    void testStoreRefreshToken() {

        // Given

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);



        // When    @Mockimport static org.junit.jupiter.api.Assertions.assertTrue;import static org.junit.jupiter.api.Assertions.assertTrue;

        String result = redisTokenService.getRefreshToken(testUserId);

        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

        // Then

        assertEquals(testRefreshToken, result);    private JwtService jwtService;

    }

        verify(valueOperations).set(

    @Test

    void testIsRefreshTokenValid() {                eq("refresh:" + testUserId.toString()),import static org.mockito.ArgumentMatchers.any;import static org.mockito.ArgumentMatchers.any;

        // Given

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);                eq(testRefreshToken),



        // When                eq(Duration.ofMillis(604800000L))    private RedisTokenService redisTokenService;

        boolean result = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);

        );

        // Then

        assertTrue(result);    }    private UUID testUserId;import static org.mockito.ArgumentMatchers.anyString;import static org.mockito.ArgumentMatchers.anyString;

    }



    @Test

    void testDeleteAccessToken() {    @Test    private String testAccessToken;

        // When

        redisTokenService.deleteAccessToken(testAccessToken);    void testIsAccessTokenValid() {



        // Then        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());    private String testRefreshToken;import static org.mockito.ArgumentMatchers.eq;import static org.mockito.ArgumentMatchers.eq;

        verify(redisTemplate).delete("access:" + testAccessToken);

    }



    @Test        boolean isValid = redisTokenService.isAccessTokenValid(testAccessToken);

    void testDeleteRefreshToken() {

        // When

        redisTokenService.deleteRefreshToken(testUserId);

        assertTrue(isValid);    @BeforeEachimport static org.mockito.Mockito.times;import static org.mockito.Mockito.times;

        // Then

        verify(redisTemplate).delete("refresh:" + testUserId.toString());    }

    }

}    void setUp() {

    @Test

    void testGetUserIdFromAccessToken() {        redisTokenService = new RedisTokenService(redisTemplate, jwtService);import static org.mockito.Mockito.verify;import static org.mockito.Mockito.verify;

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());

        testUserId = UUID.randomUUID();

        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);

        testAccessToken = "test.access.token";import static org.mockito.Mockito.when;import static org.mockito.Mockito.when;

        assertEquals(testUserId, extractedUserId);

    }        testRefreshToken = "test.refresh.token";



    @Test

    void testGetRefreshToken() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);        when(redisTemplate.opsForValue()).thenReturn(valueOperations);



        String retrievedToken = redisTokenService.getRefreshToken(testUserId);    }@ExtendWith(MockitoExtension.class)@ExtendWith(MockitoExtension.class)



        assertEquals(testRefreshToken, retrievedToken);

    }

    @Testclass RedisTokenServiceTest {class RedisTokenServiceTest {

    @Test

    void testIsRefreshTokenValid() {    void testStoreAccessToken() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);

        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);



        assertTrue(isValid);

    }        redisTokenService.storeAccessToken(testAccessToken, testUserId);    @Mock    @Mock



    @Test

    void testDeleteAccessToken() {

        redisTokenService.deleteAccessToken(testAccessToken);        verify(valueOperations).set(    private RedisTemplate<String, Object> redisTemplate;    private RedisTemplate<String, Object> redisTemplate;



        verify(redisTemplate).delete("access:" + testAccessToken);                eq("access:" + testAccessToken),

    }

                eq(testUserId.toString()),

    @Test

    void testDeleteRefreshToken() {                eq(Duration.ofMillis(900000L))

        redisTokenService.deleteRefreshToken(testUserId);

        );    @Mock    @Mock

        verify(redisTemplate).delete("refresh:" + testUserId.toString());

    }    }

}
    private ValueOperations<String, Object> valueOperations;    private ValueOperations<String, Object> valueOperations;

    @Test

    void testStoreRefreshToken() {

        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

    @Mock    @Mock

        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

    private JwtService jwtService;    private JwtService jwtService;

        verify(valueOperations).set(

                eq("refresh:" + testUserId.toString()),

                eq(testRefreshToken),

                eq(Duration.ofMillis(604800000L))    private RedisTokenService redisTokenService;    private RedisTokenService redisTokenService;

        );

    }    private UUID testUserId;    private UUID testUserId;



    @Test    private String testAccessToken;    private String testAccessToken;

    void testIsAccessTokenValid() {

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());    private String testRefreshToken;    private String testRefreshToken;



        boolean isValid = redisTokenService.isAccessTokenValid(testAccessToken);



        assertTrue(isValid);    @BeforeEach    @BeforeEach

        verify(valueOperations).get("access:" + testAccessToken);

    }    void setUp() {    void setUp() {



    @Test        redisTokenService = new RedisTokenService(redisTemplate, jwtService);        redisTokenService = new RedisTokenService(redisTemplate, jwtService);

    void testIsAccessTokenValidWhenNotExists() {

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);        testUserId = UUID.randomUUID();        testUserId = UUID.randomUUID();



        boolean isValid = redisTokenService.isAccessTokenValid(testAccessToken);        testAccessToken = "test.access.token";        testAccessToken = "test.access.token";



        assertFalse(isValid);        testRefreshToken = "test.refresh.token";        testRefreshToken = "test.refresh.token";

        verify(valueOperations).get("access:" + testAccessToken);

    }



    @Test        when(redisTemplate.opsForValue()).thenReturn(valueOperations);        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    void testGetUserIdFromAccessToken() {

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());    }    }    @Test



        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);



        assertEquals(testUserId, extractedUserId);    @Test    void testStoreAccessToken() {

        verify(valueOperations).get("access:" + testAccessToken);

    }    void testStoreAccessToken() {



    @Test        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L); // 15 minutes    @Test        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L); // 15 minutes

    void testGetUserIdFromAccessTokenWhenNotExists() {

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);



        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);        redisTokenService.storeAccessToken(testAccessToken, testUserId);    void testStoreAccessToken() {



        assertNull(extractedUserId);

        verify(valueOperations).get("access:" + testAccessToken);

    }        verify(valueOperations).set(        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L); // 15 minutes        redisTokenService.storeAccessToken(testUserId, testAccessToken);



    @Test                eq("access:" + testAccessToken),

    void testGetRefreshToken() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);                eq(testUserId.toString()),



        String retrievedToken = redisTokenService.getRefreshToken(testUserId);                eq(Duration.ofMillis(900000L))



        assertEquals(testRefreshToken, retrievedToken);        );        redisTokenService.storeAccessToken(testAccessToken, testUserId);        verify(valueOperations).set(

        verify(valueOperations).get("refresh:" + testUserId.toString());

    }    }



    @Test                eq("access_token:" + testUserId),

    void testIsRefreshTokenValid() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);    @Test



        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);    void testStoreRefreshToken() {        verify(valueOperations).set(                eq(testAccessToken),



        assertTrue(isValid);        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L); // 7 days

        verify(valueOperations).get("refresh:" + testUserId.toString());

    }                eq("access:" + testAccessToken),                eq(Duration.ofMillis(900000L))



    @Test        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

    void testIsRefreshTokenValidWhenNotMatch() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn("different.token");                eq(testUserId.toString()),        );



        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);        verify(valueOperations).set(



        assertFalse(isValid);                eq("refresh:" + testUserId.toString()),                eq(Duration.ofMillis(900000L))    }

        verify(valueOperations).get("refresh:" + testUserId.toString());

    }                eq(testRefreshToken),



    @Test                eq(Duration.ofMillis(604800000L))        );

    void testDeleteAccessToken() {

        when(redisTemplate.delete("access:" + testAccessToken)).thenReturn(true);        );



        redisTokenService.deleteAccessToken(testAccessToken);    }    }    @Test



        verify(redisTemplate).delete("access:" + testAccessToken);

    }

    @Test    void testStoreRefreshToken() {

    @Test

    void testDeleteRefreshToken() {    void testIsAccessTokenValidWhenTokenExists() {

        when(redisTemplate.delete("refresh:" + testUserId.toString())).thenReturn(true);

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());    @Test        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L); // 7 days

        redisTokenService.deleteRefreshToken(testUserId);



        verify(redisTemplate).delete("refresh:" + testUserId.toString());

    }        boolean isValid = redisTokenService.isAccessTokenValid(testAccessToken);    void testStoreRefreshToken() {



    @Test

    void testDeleteAllTokensForUser() {

        when(redisTemplate.delete("access:" + testAccessToken)).thenReturn(true);        assertTrue(isValid);        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L); // 7 days        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

        when(redisTemplate.delete("refresh:" + testUserId.toString())).thenReturn(true);

        verify(valueOperations).get("access:" + testAccessToken);

        redisTokenService.deleteAllTokensForUser(testUserId, testAccessToken);

    }

        verify(redisTemplate).delete("access:" + testAccessToken);

        verify(redisTemplate).delete("refresh:" + testUserId.toString());

    }

    @Test        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);        verify(valueOperations).set(

    @Test

    void testGetAccessTokenTTL() {    void testIsAccessTokenValidWhenTokenDoesNotExist() {

        long expectedTTL = 300L;

        when(redisTemplate.getExpire("access:" + testAccessToken)).thenReturn(expectedTTL);        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);                eq("refresh_token:" + testUserId),



        Long actualTTL = redisTokenService.getAccessTokenTTL(testAccessToken);



        assertEquals(expectedTTL, actualTTL);        boolean isValid = redisTokenService.isAccessTokenValid(testAccessToken);        verify(valueOperations).set(                eq(testRefreshToken),

        verify(redisTemplate).getExpire("access:" + testAccessToken);

    }



    @Test        assertFalse(isValid);                eq("refresh:" + testUserId.toString()),                eq(Duration.ofMillis(604800000L))

    void testHasActiveRefreshToken() {

        when(redisTemplate.hasKey("refresh:" + testUserId.toString())).thenReturn(true);        verify(valueOperations).get("access:" + testAccessToken);



        boolean hasActive = redisTokenService.hasActiveRefreshToken(testUserId);    }                eq(testRefreshToken),        );



        assertTrue(hasActive);

        verify(redisTemplate).hasKey("refresh:" + testUserId.toString());

    }    @Test                eq(Duration.ofMillis(604800000L))    }



    @Test    void testGetUserIdFromAccessToken() {

    void testExtendAccessTokenTTL() {

        Duration newTTL = Duration.ofHours(1);        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());        );

        when(redisTemplate.hasKey("access:" + testAccessToken)).thenReturn(true);

        when(redisTemplate.expire("access:" + testAccessToken, newTTL)).thenReturn(true);



        redisTokenService.extendAccessTokenTTL(testAccessToken, newTTL);        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);    }    @Test



        verify(redisTemplate).hasKey("access:" + testAccessToken);

        verify(redisTemplate).expire("access:" + testAccessToken, newTTL);

    }        assertEquals(testUserId, extractedUserId);    void testIsAccessTokenValidWhenTokenExists() {

}
        verify(valueOperations).get("access:" + testAccessToken);

    }    @Test        when(valueOperations.get("access_token:" + testUserId)).thenReturn(testAccessToken);



    @Test    void testIsAccessTokenValidWhenTokenExists() {

    void testGetUserIdFromAccessTokenWhenNotExists() {

        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());        boolean isValid = redisTokenService.isAccessTokenValid(testUserId, testAccessToken);



        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);



        assertNull(extractedUserId);        boolean isValid = redisTokenService.isAccessTokenValid(testAccessToken);        assertTrue(isValid);

        verify(valueOperations).get("access:" + testAccessToken);

    }        verify(valueOperations).get("access_token:" + testUserId);



    @Test        assertTrue(isValid);    }

    void testGetUserIdFromAccessTokenWithInvalidUUID() {

        when(valueOperations.get("access:" + testAccessToken)).thenReturn("invalid-uuid");        verify(valueOperations).get("access:" + testAccessToken);



        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);    }    @Test



        assertNull(extractedUserId);    void testIsAccessTokenValidWhenTokenDoesNotExist() {

        verify(valueOperations).get("access:" + testAccessToken);

    }    @Test        when(valueOperations.get("access_token:" + testUserId)).thenReturn(null);



    @Test    void testIsAccessTokenValidWhenTokenDoesNotExist() {

    void testGetRefreshToken() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);        boolean isValid = redisTokenService.isAccessTokenValid(testUserId, testAccessToken);



        String retrievedToken = redisTokenService.getRefreshToken(testUserId);



        assertEquals(testRefreshToken, retrievedToken);        boolean isValid = redisTokenService.isAccessTokenValid(testAccessToken);        assertFalse(isValid);

        verify(valueOperations).get("refresh:" + testUserId.toString());

    }        verify(valueOperations).get("access_token:" + testUserId);



    @Test        assertFalse(isValid);    }

    void testGetRefreshTokenWhenNotExists() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(null);        verify(valueOperations).get("access:" + testAccessToken);



        String retrievedToken = redisTokenService.getRefreshToken(testUserId);    }    @Test



        assertNull(retrievedToken);    void testIsAccessTokenValidWhenTokenMismatch() {

        verify(valueOperations).get("refresh:" + testUserId.toString());

    }    @Test        when(valueOperations.get("access_token:" + testUserId)).thenReturn("different.token");



    @Test    void testGetUserIdFromAccessToken() {

    void testIsRefreshTokenValidWhenTokenMatches() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());        boolean isValid = redisTokenService.isAccessTokenValid(testUserId, testAccessToken);



        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);



        assertTrue(isValid);        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);        assertFalse(isValid);

        verify(valueOperations).get("refresh:" + testUserId.toString());

    }        verify(valueOperations).get("access_token:" + testUserId);



    @Test        assertEquals(testUserId, extractedUserId);    }

    void testIsRefreshTokenValidWhenTokenDoesNotMatch() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn("different.token");        verify(valueOperations).get("access:" + testAccessToken);



        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);    }    @Test



        assertFalse(isValid);    void testIsRefreshTokenValidWhenTokenExists() {

        verify(valueOperations).get("refresh:" + testUserId.toString());

    }    @Test        when(valueOperations.get("refresh_token:" + testUserId)).thenReturn(testRefreshToken);



    @Test    void testGetUserIdFromAccessTokenWhenNotExists() {

    void testIsRefreshTokenValidWhenTokenDoesNotExist() {

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(null);        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);



        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);



        assertFalse(isValid);        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);        assertTrue(isValid);

        verify(valueOperations).get("refresh:" + testUserId.toString());

    }        verify(valueOperations).get("refresh_token:" + testUserId);



    @Test        assertNull(extractedUserId);    }

    void testDeleteAccessToken() {

        when(redisTemplate.delete("access:" + testAccessToken)).thenReturn(true);        verify(valueOperations).get("access:" + testAccessToken);



        redisTokenService.deleteAccessToken(testAccessToken);    }    @Test



        verify(redisTemplate).delete("access:" + testAccessToken);    void testIsRefreshTokenValidWhenTokenDoesNotExist() {

    }

    @Test        when(valueOperations.get("refresh_token:" + testUserId)).thenReturn(null);

    @Test

    void testDeleteRefreshToken() {    void testGetUserIdFromAccessTokenWithInvalidUUID() {

        when(redisTemplate.delete("refresh:" + testUserId.toString())).thenReturn(true);

        when(valueOperations.get("access:" + testAccessToken)).thenReturn("invalid-uuid");        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);

        redisTokenService.deleteRefreshToken(testUserId);



        verify(redisTemplate).delete("refresh:" + testUserId.toString());

    }        UUID extractedUserId = redisTokenService.getUserIdFromAccessToken(testAccessToken);        assertFalse(isValid);



    @Test        verify(valueOperations).get("refresh_token:" + testUserId);

    void testDeleteAllTokensForUser() {

        when(redisTemplate.delete("access:" + testAccessToken)).thenReturn(true);        assertNull(extractedUserId);    }

        when(redisTemplate.delete("refresh:" + testUserId.toString())).thenReturn(true);

        verify(valueOperations).get("access:" + testAccessToken);

        redisTokenService.deleteAllTokensForUser(testUserId, testAccessToken);

    }    @Test

        verify(redisTemplate).delete("access:" + testAccessToken);

        verify(redisTemplate).delete("refresh:" + testUserId.toString());    void testIsRefreshTokenValidWhenTokenMismatch() {

    }

    @Test        when(valueOperations.get("refresh_token:" + testUserId)).thenReturn("different.token");

    @Test

    void testRotateRefreshToken() {    void testGetRefreshToken() {

        String newRefreshToken = "new.refresh.token";

        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);

        when(redisTemplate.delete("refresh:" + testUserId.toString())).thenReturn(true);



        redisTokenService.rotateRefreshToken(testUserId, newRefreshToken);

        String retrievedToken = redisTokenService.getRefreshToken(testUserId);        assertFalse(isValid);

        verify(redisTemplate).delete("refresh:" + testUserId.toString());

        verify(valueOperations).set(        verify(valueOperations).get("refresh_token:" + testUserId);

                eq("refresh:" + testUserId.toString()),

                eq(newRefreshToken),        assertEquals(testRefreshToken, retrievedToken);    }

                eq(Duration.ofMillis(604800000L))

        );        verify(valueOperations).get("refresh:" + testUserId.toString());

    }

    }    @Test

    @Test

    void testGetAccessTokenTTL() {    void testDeleteAccessToken() {

        long expectedTTL = 300L; // 5 minutes

        when(redisTemplate.getExpire("access:" + testAccessToken)).thenReturn(expectedTTL);    @Test        redisTokenService.deleteAccessToken(testUserId);



        Long actualTTL = redisTokenService.getAccessTokenTTL(testAccessToken);    void testGetRefreshTokenWhenNotExists() {



        assertEquals(expectedTTL, actualTTL);        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(null);        verify(redisTemplate).delete("access_token:" + testUserId);

        verify(redisTemplate).getExpire("access:" + testAccessToken);

    }    }



    @Test        String retrievedToken = redisTokenService.getRefreshToken(testUserId);

    void testGetRefreshTokenTTL() {

        long expectedTTL = 86400L; // 1 day    @Test

        when(redisTemplate.getExpire("refresh:" + testUserId.toString())).thenReturn(expectedTTL);

        assertNull(retrievedToken);    void testDeleteRefreshToken() {

        Long actualTTL = redisTokenService.getRefreshTokenTTL(testUserId);

        verify(valueOperations).get("refresh:" + testUserId.toString());        redisTokenService.deleteRefreshToken(testUserId);

        assertEquals(expectedTTL, actualTTL);

        verify(redisTemplate).getExpire("refresh:" + testUserId.toString());    }

    }

        verify(redisTemplate).delete("refresh_token:" + testUserId);

    @Test

    void testHasActiveRefreshToken() {    @Test    }

        when(redisTemplate.hasKey("refresh:" + testUserId.toString())).thenReturn(true);

    void testIsRefreshTokenValidWhenTokenMatches() {

        boolean hasActive = redisTokenService.hasActiveRefreshToken(testUserId);

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);    @Test

        assertTrue(hasActive);

        verify(redisTemplate).hasKey("refresh:" + testUserId.toString());    void testDeleteAllTokens() {

    }

        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);        redisTokenService.deleteAllTokens(testUserId);

    @Test

    void testHasActiveRefreshTokenWhenNotExists() {

        when(redisTemplate.hasKey("refresh:" + testUserId.toString())).thenReturn(false);

        assertTrue(isValid);        verify(redisTemplate).delete("access_token:" + testUserId);

        boolean hasActive = redisTokenService.hasActiveRefreshToken(testUserId);

        verify(valueOperations).get("refresh:" + testUserId.toString());        verify(redisTemplate).delete("refresh_token:" + testUserId);

        assertFalse(hasActive);

        verify(redisTemplate).hasKey("refresh:" + testUserId.toString());    }    }

    }



    @Test

    void testExtendAccessTokenTTL() {    @Test    @Test

        Duration newTTL = Duration.ofHours(1);

        when(redisTemplate.hasKey("access:" + testAccessToken)).thenReturn(true);    void testIsRefreshTokenValidWhenTokenDoesNotMatch() {    void testStoreTokensWithSameUserId() {

        when(redisTemplate.expire("access:" + testAccessToken, newTTL)).thenReturn(true);

        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn("different.token");        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        redisTokenService.extendAccessTokenTTL(testAccessToken, newTTL);

        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        verify(redisTemplate).hasKey("access:" + testAccessToken);

        verify(redisTemplate).expire("access:" + testAccessToken, newTTL);        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);

    }

        redisTokenService.storeAccessToken(testUserId, testAccessToken);

    @Test

    void testExtendAccessTokenTTLWhenTokenDoesNotExist() {        assertFalse(isValid);        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

        Duration newTTL = Duration.ofHours(1);

        when(redisTemplate.hasKey("access:" + testAccessToken)).thenReturn(false);        verify(valueOperations).get("refresh:" + testUserId.toString());



        redisTokenService.extendAccessTokenTTL(testAccessToken, newTTL);    }        verify(valueOperations).set(



        verify(redisTemplate).hasKey("access:" + testAccessToken);                eq("access_token:" + testUserId),

        verify(redisTemplate, times(0)).expire(anyString(), any(Duration.class));

    }    @Test                eq(testAccessToken),



    @Test    void testIsRefreshTokenValidWhenTokenDoesNotExist() {                any(Duration.class)

    void testStoreMultipleTokensForSameUser() {

        String accessToken1 = "access.token.1";        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(null);        );

        String accessToken2 = "access.token.2";

        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);        verify(valueOperations).set(



        redisTokenService.storeAccessToken(accessToken1, testUserId);        boolean isValid = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);                eq("refresh_token:" + testUserId),

        redisTokenService.storeAccessToken(accessToken2, testUserId);

                eq(testRefreshToken),

        verify(valueOperations).set(

                eq("access:" + accessToken1),        assertFalse(isValid);                any(Duration.class)

                eq(testUserId.toString()),

                any(Duration.class)        verify(valueOperations).get("refresh:" + testUserId.toString());        );

        );

        verify(valueOperations).set(    }    }

                eq("access:" + accessToken2),

                eq(testUserId.toString()),

                any(Duration.class)

        );    @Test    @Test

    }

    void testDeleteAccessToken() {    void testOverwriteExistingTokens() {

    @Test

    void testStoreRefreshTokenOverwritesPrevious() {        when(redisTemplate.delete("access:" + testAccessToken)).thenReturn(true);        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        String refreshToken1 = "refresh.token.1";

        String refreshToken2 = "refresh.token.2";        String newAccessToken = "new.access.token";

        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        redisTokenService.deleteAccessToken(testAccessToken);

        redisTokenService.storeRefreshToken(testUserId, refreshToken1);

        redisTokenService.storeRefreshToken(testUserId, refreshToken2);        // Store first token



        // Should use the same key for both, effectively overwriting        verify(redisTemplate).delete("access:" + testAccessToken);        redisTokenService.storeAccessToken(testUserId, testAccessToken);

        verify(valueOperations, times(2)).set(

                eq("refresh:" + testUserId.toString()),    }        // Store second token (should overwrite)

                anyString(),

                any(Duration.class)        redisTokenService.storeAccessToken(testUserId, newAccessToken);

        );

    }    @Test



    @Test    void testDeleteRefreshToken() {        verify(valueOperations, times(2)).set(

    void testCleanupExpiredTokens() {

        // This method only logs, so we just verify it doesn't throw exceptions        when(redisTemplate.delete("refresh:" + testUserId.toString())).thenReturn(true);                eq("access_token:" + testUserId),

        redisTokenService.cleanupExpiredTokens();

        // No assertions needed as this is a logging-only method                anyString(),

    }

        redisTokenService.deleteRefreshToken(testUserId);                any(Duration.class)

    @Test

    void testTokenKeyPrefixes() {        );

        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);        verify(redisTemplate).delete("refresh:" + testUserId.toString());    }



        redisTokenService.storeAccessToken(testAccessToken, testUserId);    }

        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

    @Test

        // Verify correct prefixes are used

        verify(valueOperations).set(    @Test    void testMultipleUsersTokens() {

                eq("access:" + testAccessToken),

                anyString(),    void testDeleteAllTokensForUser() {        UUID userId1 = UUID.randomUUID();

                any(Duration.class)

        );        when(redisTemplate.delete("access:" + testAccessToken)).thenReturn(true);        UUID userId2 = UUID.randomUUID();

        verify(valueOperations).set(

                eq("refresh:" + testUserId.toString()),        when(redisTemplate.delete("refresh:" + testUserId.toString())).thenReturn(true);        String token1 = "token.for.user1";

                anyString(),

                any(Duration.class)        String token2 = "token.for.user2";

        );

    }        redisTokenService.deleteAllTokensForUser(testUserId, testAccessToken);



    @Test        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

    void testMultipleUsersIndependentTokens() {

        UUID userId1 = UUID.randomUUID();        verify(redisTemplate).delete("access:" + testAccessToken);

        UUID userId2 = UUID.randomUUID();

        String refreshToken1 = "refresh.token.user1";        verify(redisTemplate).delete("refresh:" + testUserId.toString());        redisTokenService.storeAccessToken(userId1, token1);

        String refreshToken2 = "refresh.token.user2";

            }        redisTokenService.storeAccessToken(userId2, token2);

        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);



        redisTokenService.storeRefreshToken(userId1, refreshToken1);

        redisTokenService.storeRefreshToken(userId2, refreshToken2);    @Test        verify(valueOperations).set(



        verify(valueOperations).set(    void testRotateRefreshToken() {                eq("access_token:" + userId1),

                eq("refresh:" + userId1.toString()),

                eq(refreshToken1),        String newRefreshToken = "new.refresh.token";                eq(token1),

                any(Duration.class)

        );        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);                any(Duration.class)

        verify(valueOperations).set(

                eq("refresh:" + userId2.toString()),        when(redisTemplate.delete("refresh:" + testUserId.toString())).thenReturn(true);        );

                eq(refreshToken2),

                any(Duration.class)        verify(valueOperations).set(

        );

    }        redisTokenService.rotateRefreshToken(testUserId, newRefreshToken);                eq("access_token:" + userId2),

}
                eq(token2),

        verify(redisTemplate).delete("refresh:" + testUserId.toString());                any(Duration.class)

        verify(valueOperations).set(        );

                eq("refresh:" + testUserId.toString()),    }

                eq(newRefreshToken),

                eq(Duration.ofMillis(604800000L))    @Test

        );    void testTokenValidationAfterDeletion() {

    }        // First store the token

        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

    @Test        redisTokenService.storeAccessToken(testUserId, testAccessToken);

    void testGetAccessTokenTTL() {

        long expectedTTL = 300L; // 5 minutes        // Simulate token exists

        when(redisTemplate.getExpire("access:" + testAccessToken)).thenReturn(expectedTTL);        when(valueOperations.get("access_token:" + testUserId)).thenReturn(testAccessToken);

        assertTrue(redisTokenService.isAccessTokenValid(testUserId, testAccessToken));

        Long actualTTL = redisTokenService.getAccessTokenTTL(testAccessToken);

        // Delete the token

        assertEquals(expectedTTL, actualTTL);        redisTokenService.deleteAccessToken(testUserId);

        verify(redisTemplate).getExpire("access:" + testAccessToken);

    }        // Simulate token no longer exists after deletion

        when(valueOperations.get("access_token:" + testUserId)).thenReturn(null);

    @Test        assertFalse(redisTokenService.isAccessTokenValid(testUserId, testAccessToken));

    void testGetRefreshTokenTTL() {    }

        long expectedTTL = 86400L; // 1 day

        when(redisTemplate.getExpire("refresh:" + testUserId.toString())).thenReturn(expectedTTL);    @Test

    void testDeleteAllTokensRemovesBothTokenTypes() {

        Long actualTTL = redisTokenService.getRefreshTokenTTL(testUserId);        redisTokenService.deleteAllTokens(testUserId);



        assertEquals(expectedTTL, actualTTL);        verify(redisTemplate).delete("access_token:" + testUserId);

        verify(redisTemplate).getExpire("refresh:" + testUserId.toString());        verify(redisTemplate).delete("refresh_token:" + testUserId);

    }    }



    @Test    @Test

    void testHasActiveRefreshToken() {    void testEmptyTokenValidation() {

        when(redisTemplate.hasKey("refresh:" + testUserId.toString())).thenReturn(true);        when(valueOperations.get("access_token:" + testUserId)).thenReturn(testAccessToken);



        boolean hasActive = redisTokenService.hasActiveRefreshToken(testUserId);        boolean isValid = redisTokenService.isAccessTokenValid(testUserId, "");



        assertTrue(hasActive);        assertFalse(isValid);

        verify(redisTemplate).hasKey("refresh:" + testUserId.toString());    }

    }

    @Test

    @Test    void testNullTokenValidation() {

    void testHasActiveRefreshTokenWhenNotExists() {        when(valueOperations.get("access_token:" + testUserId)).thenReturn(testAccessToken);

        when(redisTemplate.hasKey("refresh:" + testUserId.toString())).thenReturn(false);

        boolean isValid = redisTokenService.isAccessTokenValid(testUserId, null);

        boolean hasActive = redisTokenService.hasActiveRefreshToken(testUserId);

        assertFalse(isValid);

        assertFalse(hasActive);    }

        verify(redisTemplate).hasKey("refresh:" + testUserId.toString());

    }    @Test

    void testWhitespaceTokenValidation() {

    @Test        when(valueOperations.get("access_token:" + testUserId)).thenReturn(testAccessToken);

    void testExtendAccessTokenTTL() {

        Duration newTTL = Duration.ofHours(1);        boolean isValid = redisTokenService.isAccessTokenValid(testUserId, "   ");

        when(redisTemplate.hasKey("access:" + testAccessToken)).thenReturn(true);

        when(redisTemplate.expire("access:" + testAccessToken, newTTL)).thenReturn(true);        assertFalse(isValid);

    }

        redisTokenService.extendAccessTokenTTL(testAccessToken, newTTL);

    @Test

        verify(redisTemplate).hasKey("access:" + testAccessToken);    void testStoreTokenWithZeroExpiration() {

        verify(redisTemplate).expire("access:" + testAccessToken, newTTL);        when(jwtService.getAccessTokenExpirationMs()).thenReturn(0L);

    }

        redisTokenService.storeAccessToken(testUserId, testAccessToken);

    @Test

    void testExtendAccessTokenTTLWhenTokenDoesNotExist() {        verify(valueOperations).set(

        Duration newTTL = Duration.ofHours(1);                eq("access_token:" + testUserId),

        when(redisTemplate.hasKey("access:" + testAccessToken)).thenReturn(false);                eq(testAccessToken),

                eq(Duration.ofMillis(0L))

        redisTokenService.extendAccessTokenTTL(testAccessToken, newTTL);        );

    }

        verify(redisTemplate).hasKey("access:" + testAccessToken);

        verify(redisTemplate, times(0)).expire(anyString(), any(Duration.class));    @Test

    }    void testStoreTokenWithNegativeExpiration() {

        when(jwtService.getAccessTokenExpirationMs()).thenReturn(-1000L);

    @Test

    void testStoreMultipleTokensForSameUser() {        redisTokenService.storeAccessToken(testUserId, testAccessToken);

        String accessToken1 = "access.token.1";

        String accessToken2 = "access.token.2";        verify(valueOperations).set(

        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);                eq("access_token:" + testUserId),

                eq(testAccessToken),

        redisTokenService.storeAccessToken(accessToken1, testUserId);                eq(Duration.ofMillis(-1000L))

        redisTokenService.storeAccessToken(accessToken2, testUserId);        );

    }

        verify(valueOperations).set(

                eq("access:" + accessToken1),    @Test

                eq(testUserId.toString()),    void testKeyGenerationConsistency() {

                any(Duration.class)        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        );

        verify(valueOperations).set(        // Store token

                eq("access:" + accessToken2),        redisTokenService.storeAccessToken(testUserId, testAccessToken);

                eq(testUserId.toString()),

                any(Duration.class)        // Check if the same key is used for validation

        );        when(valueOperations.get("access_token:" + testUserId)).thenReturn(testAccessToken);

    }        assertTrue(redisTokenService.isAccessTokenValid(testUserId, testAccessToken));



    @Test        // Delete using the same key pattern

    void testStoreRefreshTokenOverwritesPrevious() {        redisTokenService.deleteAccessToken(testUserId);

        String refreshToken1 = "refresh.token.1";

        String refreshToken2 = "refresh.token.2";        verify(valueOperations).set(eq("access_token:" + testUserId), anyString(), any(Duration.class));

        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);        verify(valueOperations).get("access_token:" + testUserId);

        verify(redisTemplate).delete("access_token:" + testUserId);

        redisTokenService.storeRefreshToken(testUserId, refreshToken1);    }

        redisTokenService.storeRefreshToken(testUserId, refreshToken2);

    @Test

        // Should use the same key for both, effectively overwriting    void testRefreshTokenKeyGenerationConsistency() {

        verify(valueOperations, times(2)).set(        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

                eq("refresh:" + testUserId.toString()),

                anyString(),        // Store token

                any(Duration.class)        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

        );

    }        // Check if the same key is used for validation

        when(valueOperations.get("refresh_token:" + testUserId)).thenReturn(testRefreshToken);

    @Test        assertTrue(redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken));

    void testCleanupExpiredTokens() {

        // This method only logs, so we just verify it doesn't throw exceptions        // Delete using the same key pattern

        redisTokenService.cleanupExpiredTokens();        redisTokenService.deleteRefreshToken(testUserId);

        // No assertions needed as this is a logging-only method

    }        verify(valueOperations).set(eq("refresh_token:" + testUserId), anyString(), any(Duration.class));

        verify(valueOperations).get("refresh_token:" + testUserId);

    @Test        verify(redisTemplate).delete("refresh_token:" + testUserId);

    void testTokenKeyPrefixes() {    }

        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);}
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        redisTokenService.storeAccessToken(testAccessToken, testUserId);
        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

        // Verify correct prefixes are used
        verify(valueOperations).set(
                eq("access:" + testAccessToken),
                anyString(),
                any(Duration.class)
        );
        verify(valueOperations).set(
                eq("refresh:" + testUserId.toString()),
                anyString(),
                any(Duration.class)
        );
    }

    @Test
    void testMultipleUsersIndependentTokens() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        String refreshToken1 = "refresh.token.user1";
        String refreshToken2 = "refresh.token.user2";

        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        redisTokenService.storeRefreshToken(userId1, refreshToken1);
        redisTokenService.storeRefreshToken(userId2, refreshToken2);

        verify(valueOperations).set(
                eq("refresh:" + userId1.toString()),
                eq(refreshToken1),
                any(Duration.class)
        );
        verify(valueOperations).set(
                eq("refresh:" + userId2.toString()),
                eq(refreshToken2),
                any(Duration.class)
        );
    }
}