package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.dto.AreaDraftRequest;
import area.server.AREA_Back.dto.AreaDraftResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.Cursor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AreaDraftCacheService Tests")
class AreaDraftCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AreaDraftCacheService areaDraftCacheService;

    private UUID userId;
    private UUID areaId;
    private AreaDraftRequest draftRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        
        draftRequest = new AreaDraftRequest();
        draftRequest.setName("Test Area");
        draftRequest.setDescription("Test Description");
        draftRequest.setLayoutMode("linear");
        draftRequest.setActions(new ArrayList<>());
        draftRequest.setReactions(new ArrayList<>());
        draftRequest.setConnections(new ArrayList<>());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("saveDraft - Should save new draft successfully with auto-generated draftId")
    void testSaveDraft_NewDraft_Success() {
        // Arrange
        doNothing().when(valueOperations).set(anyString(), any(AreaDraftRequest.class), any(Duration.class));

        // Act
        String draftId = areaDraftCacheService.saveDraft(userId, draftRequest);

        // Assert
        assertNotNull(draftId);
        assertNotNull(draftRequest.getSavedAt());
        verify(valueOperations).set(
            eq("area:draft:new:" + userId + ":" + draftId),
            eq(draftRequest),
            eq(Duration.ofHours(1))
        );
    }

    @Test
    @DisplayName("saveDraft - Should save draft with existing draftId")
    void testSaveDraft_ExistingDraftId_Success() {
        // Arrange
        String existingDraftId = UUID.randomUUID().toString();
        draftRequest.setDraftId(existingDraftId);
        doNothing().when(valueOperations).set(anyString(), any(AreaDraftRequest.class), any(Duration.class));

        // Act
        String draftId = areaDraftCacheService.saveDraft(userId, draftRequest);

        // Assert
        assertEquals(existingDraftId, draftId);
        verify(valueOperations).set(
            eq("area:draft:new:" + userId + ":" + existingDraftId),
            eq(draftRequest),
            eq(Duration.ofHours(1))
        );
    }

    @Test
    @DisplayName("saveDraft - Should save edit draft with areaId")
    void testSaveDraft_WithAreaId_Success() {
        // Arrange
        doNothing().when(valueOperations).set(anyString(), any(AreaDraftRequest.class), any(Duration.class));

        // Act
        String draftId = areaDraftCacheService.saveDraft(userId, draftRequest, areaId);

        // Assert
        assertEquals(areaId.toString(), draftId);
        verify(valueOperations).set(
            eq("area:draft:edit:" + areaId + ":" + userId),
            eq(draftRequest),
            eq(Duration.ofHours(1))
        );
    }

    @Test
    @DisplayName("saveDraft - Should preserve savedAt if already set")
    void testSaveDraft_PreservesSavedAt() {
        // Arrange
        LocalDateTime savedAt = LocalDateTime.now().minusMinutes(5);
        draftRequest.setSavedAt(savedAt);
        doNothing().when(valueOperations).set(anyString(), any(AreaDraftRequest.class), any(Duration.class));

        // Act
        areaDraftCacheService.saveDraft(userId, draftRequest);

        // Assert
        assertEquals(savedAt, draftRequest.getSavedAt());
    }

    @Test
    @DisplayName("saveDraft - Should throw exception on Redis connection failure")
    void testSaveDraft_RedisConnectionFailure() {
        // Arrange
        doThrow(new RedisConnectionFailureException("Connection failed"))
            .when(valueOperations).set(anyString(), any(AreaDraftRequest.class), any(Duration.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> areaDraftCacheService.saveDraft(userId, draftRequest));
        assertEquals("Cache service unavailable", exception.getMessage());
    }

    @Test
    @DisplayName("saveDraft - Should throw exception on general error")
    void testSaveDraft_GeneralError() {
        // Arrange
        doThrow(new RuntimeException("Unexpected error"))
            .when(valueOperations).set(anyString(), any(AreaDraftRequest.class), any(Duration.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> areaDraftCacheService.saveDraft(userId, draftRequest));
        assertEquals("Failed to save draft", exception.getMessage());
    }

    @Test
    @DisplayName("getDraft - Should retrieve new draft successfully")
    void testGetDraft_NewDraft_Success() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String keyNew = "area:draft:new:" + userId + ":" + draftId;
        draftRequest.setSavedAt(LocalDateTime.now());
        
        when(valueOperations.get(keyNew)).thenReturn(draftRequest);
        when(redisTemplate.getExpire(keyNew, TimeUnit.SECONDS)).thenReturn(3600L);

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getDraft(userId, draftId);

        // Assert
        assertTrue(result.isPresent());
        AreaDraftResponse response = result.get();
        assertEquals(draftId, response.getDraftId());
        assertEquals(draftRequest.getName(), response.getName());
        assertEquals(draftRequest.getDescription(), response.getDescription());
        assertEquals(userId, response.getUserId());
        assertEquals(3600L, response.getTtlSeconds());
    }

    @Test
    @DisplayName("getDraft - Should retrieve edit draft successfully")
    void testGetDraft_EditDraft_Success() {
        // Arrange
        String draftId = areaId.toString();
        String keyNew = "area:draft:new:" + userId + ":" + draftId;
        String keyEdit = "area:draft:edit:" + draftId + ":" + userId;
        draftRequest.setSavedAt(LocalDateTime.now());
        
        when(valueOperations.get(keyNew)).thenReturn(null);
        when(valueOperations.get(keyEdit)).thenReturn(draftRequest);
        when(redisTemplate.getExpire(keyEdit, TimeUnit.SECONDS)).thenReturn(3600L);

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getDraft(userId, draftId);

        // Assert
        assertTrue(result.isPresent());
        AreaDraftResponse response = result.get();
        assertEquals(draftId, response.getDraftId());
        assertEquals(draftRequest.getName(), response.getName());
    }

    @Test
    @DisplayName("getDraft - Should return empty when draft not found")
    void testGetDraft_NotFound() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getDraft(userId, draftId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getDraft - Should return empty on Redis connection failure")
    void testGetDraft_RedisConnectionFailure() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        when(valueOperations.get(anyString()))
            .thenThrow(new RedisConnectionFailureException("Connection failed"));

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getDraft(userId, draftId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getDraft - Should handle TTL retrieval exception")
    void testGetDraft_TTLException() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String keyNew = "area:draft:new:" + userId + ":" + draftId;
        draftRequest.setSavedAt(LocalDateTime.now());
        
        when(valueOperations.get(keyNew)).thenReturn(draftRequest);
        when(redisTemplate.getExpire(keyNew, TimeUnit.SECONDS))
            .thenThrow(new RuntimeException("TTL error"));

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getDraft(userId, draftId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().getTtlSeconds());
    }

    @Test
    @DisplayName("getUserDrafts - Should retrieve all user drafts successfully")
    @SuppressWarnings("unchecked")
    void testGetUserDrafts_Success() {
        // Arrange
        String draftId1 = UUID.randomUUID().toString();
        String draftId2 = UUID.randomUUID().toString();
        String key1 = "area:draft:new:" + userId + ":" + draftId1;
        String key2 = "area:draft:new:" + userId + ":" + draftId2;

        AreaDraftRequest draft1 = new AreaDraftRequest();
        draft1.setName("Draft 1");
        draft1.setSavedAt(LocalDateTime.now());

        AreaDraftRequest draft2 = new AreaDraftRequest();
        draft2.setName("Draft 2");
        draft2.setSavedAt(LocalDateTime.now());

        Cursor<byte[]> mockCursor = mock(Cursor.class);
        when(mockCursor.hasNext()).thenReturn(true, true, false);
        when(mockCursor.next()).thenReturn(key1.getBytes(), key2.getBytes());

        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            org.springframework.data.redis.connection.RedisConnection connection = 
                mock(org.springframework.data.redis.connection.RedisConnection.class);
            when(connection.scan(any(ScanOptions.class))).thenReturn(mockCursor);
            return callback.doInRedis(connection);
        });

        when(valueOperations.get(key1)).thenReturn(draft1);
        when(valueOperations.get(key2)).thenReturn(draft2);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(3600L);

        // Act
        List<AreaDraftResponse> result = areaDraftCacheService.getUserDrafts(userId);

        // Assert
        assertEquals(2, result.size());
        assertEquals("Draft 1", result.get(0).getName());
        assertEquals("Draft 2", result.get(1).getName());
    }

    @Test
    @DisplayName("getUserDrafts - Should return empty list on Redis connection failure")
    @SuppressWarnings("unchecked")
    void testGetUserDrafts_RedisConnectionFailure() {
        // Arrange
        when(redisTemplate.execute(any(RedisCallback.class)))
            .thenThrow(new RedisConnectionFailureException("Connection failed"));

        // Act
        List<AreaDraftResponse> result = areaDraftCacheService.getUserDrafts(userId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getUserDrafts - Should skip invalid draft objects")
    @SuppressWarnings("unchecked")
    void testGetUserDrafts_InvalidDraftObject() {
        // Arrange
        String draftId1 = UUID.randomUUID().toString();
        String key1 = "area:draft:new:" + userId + ":" + draftId1;

        Cursor<byte[]> mockCursor = mock(Cursor.class);
        when(mockCursor.hasNext()).thenReturn(true, false);
        when(mockCursor.next()).thenReturn(key1.getBytes());

        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            org.springframework.data.redis.connection.RedisConnection connection = 
                mock(org.springframework.data.redis.connection.RedisConnection.class);
            when(connection.scan(any(ScanOptions.class))).thenReturn(mockCursor);
            return callback.doInRedis(connection);
        });

        // Return a String instead of AreaDraftRequest
        when(valueOperations.get(key1)).thenReturn("Invalid Object");

        // Act
        List<AreaDraftResponse> result = areaDraftCacheService.getUserDrafts(userId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getEditDraft - Should retrieve edit draft successfully")
    void testGetEditDraft_Success() {
        // Arrange
        String key = "area:draft:edit:" + areaId + ":" + userId;
        draftRequest.setSavedAt(LocalDateTime.now());
        
        when(valueOperations.get(key)).thenReturn(draftRequest);
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(3600L);

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getEditDraft(userId, areaId);

        // Assert
        assertTrue(result.isPresent());
        AreaDraftResponse response = result.get();
        assertEquals(areaId.toString(), response.getDraftId());
        assertEquals(draftRequest.getName(), response.getName());
    }

    @Test
    @DisplayName("getEditDraft - Should return empty when draft not found")
    void testGetEditDraft_NotFound() {
        // Arrange
        String key = "area:draft:edit:" + areaId + ":" + userId;
        when(valueOperations.get(key)).thenReturn(null);

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getEditDraft(userId, areaId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getEditDraft - Should return empty on Redis connection failure")
    void testGetEditDraft_RedisConnectionFailure() {
        // Arrange
        when(valueOperations.get(anyString()))
            .thenThrow(new RedisConnectionFailureException("Connection failed"));

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getEditDraft(userId, areaId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("deleteDraft - Should delete new draft successfully")
    void testDeleteDraft_NewDraft_Success() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String keyNew = "area:draft:new:" + userId + ":" + draftId;
        String keyEdit = "area:draft:edit:" + draftId + ":" + userId;
        
        when(redisTemplate.delete(keyNew)).thenReturn(true);
        when(redisTemplate.delete(keyEdit)).thenReturn(false);

        // Act
        areaDraftCacheService.deleteDraft(userId, draftId);

        // Assert
        verify(redisTemplate).delete(keyNew);
        verify(redisTemplate).delete(keyEdit);
    }

    @Test
    @DisplayName("deleteDraft - Should delete edit draft successfully")
    void testDeleteDraft_EditDraft_Success() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String keyNew = "area:draft:new:" + userId + ":" + draftId;
        String keyEdit = "area:draft:edit:" + draftId + ":" + userId;
        
        when(redisTemplate.delete(keyNew)).thenReturn(false);
        when(redisTemplate.delete(keyEdit)).thenReturn(true);

        // Act
        areaDraftCacheService.deleteDraft(userId, draftId);

        // Assert
        verify(redisTemplate).delete(keyNew);
        verify(redisTemplate).delete(keyEdit);
    }

    @Test
    @DisplayName("deleteDraft - Should handle when draft not found")
    void testDeleteDraft_NotFound() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        when(redisTemplate.delete(anyString())).thenReturn(false);

        // Act
        areaDraftCacheService.deleteDraft(userId, draftId);

        // Assert
        verify(redisTemplate, times(2)).delete(anyString());
    }

    @Test
    @DisplayName("deleteDraft - Should throw exception on Redis connection failure")
    void testDeleteDraft_RedisConnectionFailure() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        when(redisTemplate.delete(anyString()))
            .thenThrow(new RedisConnectionFailureException("Connection failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> areaDraftCacheService.deleteDraft(userId, draftId));
        assertEquals("Cache service unavailable", exception.getMessage());
    }

    @Test
    @DisplayName("extendDraftTTL - Should extend new draft TTL successfully")
    void testExtendDraftTTL_NewDraft_Success() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String keyNew = "area:draft:new:" + userId + ":" + draftId;
        String keyEdit = "area:draft:edit:" + draftId + ":" + userId;
        
        when(redisTemplate.expire(keyNew, Duration.ofHours(1))).thenReturn(true);
        when(redisTemplate.expire(keyEdit, Duration.ofHours(1))).thenReturn(false);

        // Act
        areaDraftCacheService.extendDraftTTL(userId, draftId);

        // Assert
        verify(redisTemplate).expire(keyNew, Duration.ofHours(1));
        verify(redisTemplate).expire(keyEdit, Duration.ofHours(1));
    }

    @Test
    @DisplayName("extendDraftTTL - Should extend edit draft TTL successfully")
    void testExtendDraftTTL_EditDraft_Success() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String keyNew = "area:draft:new:" + userId + ":" + draftId;
        String keyEdit = "area:draft:edit:" + draftId + ":" + userId;
        
        when(redisTemplate.expire(keyNew, Duration.ofHours(1))).thenReturn(false);
        when(redisTemplate.expire(keyEdit, Duration.ofHours(1))).thenReturn(true);

        // Act
        areaDraftCacheService.extendDraftTTL(userId, draftId);

        // Assert
        verify(redisTemplate).expire(keyNew, Duration.ofHours(1));
        verify(redisTemplate).expire(keyEdit, Duration.ofHours(1));
    }

    @Test
    @DisplayName("extendDraftTTL - Should handle when draft not found")
    void testExtendDraftTTL_NotFound() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(false);

        // Act
        areaDraftCacheService.extendDraftTTL(userId, draftId);

        // Assert
        verify(redisTemplate, times(2)).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("extendDraftTTL - Should handle Redis connection failure gracefully")
    void testExtendDraftTTL_RedisConnectionFailure() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        when(redisTemplate.expire(anyString(), any(Duration.class)))
            .thenThrow(new RedisConnectionFailureException("Connection failed"));

        // Act - Should not throw exception
        assertDoesNotThrow(() -> areaDraftCacheService.extendDraftTTL(userId, draftId));
    }

    @Test
    @DisplayName("isCacheAvailable - Should return true when cache is available")
    void testIsCacheAvailable_Available() {
        // Arrange
        when(valueOperations.get("test:connection")).thenReturn(null);

        // Act
        boolean result = areaDraftCacheService.isCacheAvailable();

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("isCacheAvailable - Should return false on Redis connection failure")
    void testIsCacheAvailable_ConnectionFailure() {
        // Arrange
        when(valueOperations.get("test:connection"))
            .thenThrow(new RedisConnectionFailureException("Connection failed"));

        // Act
        boolean result = areaDraftCacheService.isCacheAvailable();

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("isCacheAvailable - Should return false on general exception")
    void testIsCacheAvailable_GeneralException() {
        // Arrange
        when(valueOperations.get("test:connection"))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        boolean result = areaDraftCacheService.isCacheAvailable();

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("saveDraft with two params - Should delegate to three param version")
    void testSaveDraft_TwoParams_Delegation() {
        // Arrange
        doNothing().when(valueOperations).set(anyString(), any(AreaDraftRequest.class), any(Duration.class));

        // Act
        String draftId = areaDraftCacheService.saveDraft(userId, draftRequest);

        // Assert
        assertNotNull(draftId);
        verify(valueOperations).set(
            contains("area:draft:new:" + userId),
            eq(draftRequest),
            eq(Duration.ofHours(1))
        );
    }

    @Test
    @DisplayName("convertToResponse - Should handle null savedAt")
    void testConvertToResponse_NullSavedAt() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String key = "area:draft:new:" + userId + ":" + draftId;
        draftRequest.setSavedAt(null); // Explicitly set to null
        
        when(valueOperations.get(key)).thenReturn(draftRequest);
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(3600L);

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getDraft(userId, draftId);

        // Assert
        assertTrue(result.isPresent());
        assertNotNull(result.get().getSavedAt());
    }

    @Test
    @DisplayName("convertToResponse - Should handle null TTL")
    void testConvertToResponse_NullTTL() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String key = "area:draft:new:" + userId + ":" + draftId;
        draftRequest.setSavedAt(LocalDateTime.now());
        
        when(valueOperations.get(key)).thenReturn(draftRequest);
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(null);

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getDraft(userId, draftId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().getTtlSeconds());
    }

    @Test
    @DisplayName("convertToResponse - Should map all fields correctly")
    void testConvertToResponse_AllFieldsMapped() {
        // Arrange
        String draftId = UUID.randomUUID().toString();
        String key = "area:draft:new:" + userId + ":" + draftId;
        
        LocalDateTime savedAt = LocalDateTime.now();
        draftRequest.setSavedAt(savedAt);
        draftRequest.setName("Test Area");
        draftRequest.setDescription("Test Description");
        draftRequest.setLayoutMode("grid");
        
        when(valueOperations.get(key)).thenReturn(draftRequest);
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(3600L);

        // Act
        Optional<AreaDraftResponse> result = areaDraftCacheService.getDraft(userId, draftId);

        // Assert
        assertTrue(result.isPresent());
        AreaDraftResponse response = result.get();
        assertEquals(draftId, response.getDraftId());
        assertEquals("Test Area", response.getName());
        assertEquals("Test Description", response.getDescription());
        assertEquals(userId, response.getUserId());
        assertEquals("grid", response.getLayoutMode());
        assertEquals(savedAt, response.getSavedAt());
        assertEquals(3600L, response.getTtlSeconds());
        assertNotNull(response.getActions());
        assertNotNull(response.getReactions());
        assertNotNull(response.getConnections());
    }
}
