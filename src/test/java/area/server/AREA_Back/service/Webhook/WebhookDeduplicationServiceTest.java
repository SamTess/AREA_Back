package area.server.AREA_Back.service.Webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookDeduplicationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private WebhookDeduplicationService deduplicationService;

    private String eventId;
    private String provider;

    @BeforeEach
    void setUp() {
        eventId = "event-123";
        provider = "github";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testIsDuplicate_NotDuplicate() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        boolean result = deduplicationService.isDuplicate(eventId, provider);

        assertFalse(result);
        verify(redisTemplate).hasKey(anyString());
    }

    @Test
    void testIsDuplicate_IsDuplicate() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        boolean result = deduplicationService.isDuplicate(eventId, provider);

        assertTrue(result);
    }

    @Test
    void testIsDuplicate_NullEventId() {
        boolean result = deduplicationService.isDuplicate(null, provider);

        assertTrue(result);
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void testIsDuplicate_EmptyEventId() {
        boolean result = deduplicationService.isDuplicate("", provider);

        assertTrue(result);
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void testIsDuplicate_WhitespaceEventId() {
        boolean result = deduplicationService.isDuplicate("   ", provider);

        assertTrue(result);
    }

    @Test
    void testIsDuplicate_RedisException() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis error"));

        boolean result = deduplicationService.isDuplicate(eventId, provider);

        assertFalse(result);
    }

    @Test
    void testMarkAsProcessed_Success() {
        assertDoesNotThrow(() -> 
            deduplicationService.markAsProcessed(eventId, provider)
        );

        verify(valueOperations).set(anyString(), anyLong(), any(Duration.class));
    }

    @Test
    void testMarkAsProcessed_NullEventId() {
        assertDoesNotThrow(() -> 
            deduplicationService.markAsProcessed(null, provider)
        );

        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
    }

    @Test
    void testMarkAsProcessed_EmptyEventId() {
        assertDoesNotThrow(() -> 
            deduplicationService.markAsProcessed("", provider)
        );

        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
    }

    @Test
    void testMarkAsProcessed_WithCustomTtl() {
        Duration customTtl = Duration.ofMinutes(10);

        assertDoesNotThrow(() -> 
            deduplicationService.markAsProcessed(eventId, provider, customTtl)
        );

        verify(valueOperations).set(anyString(), anyLong(), eq(customTtl));
    }

    @Test
    void testMarkAsProcessed_GitHubProvider() {
        deduplicationService.markAsProcessed(eventId, "github");

        verify(valueOperations).set(anyString(), anyLong(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void testMarkAsProcessed_SlackProvider() {
        deduplicationService.markAsProcessed(eventId, "slack");

        verify(valueOperations).set(anyString(), anyLong(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void testMarkAsProcessed_GenericProvider() {
        deduplicationService.markAsProcessed(eventId, "other");

        verify(valueOperations).set(anyString(), anyLong(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void testMarkAsProcessed_RedisException() {
        doThrow(new RuntimeException("Redis error"))
            .when(valueOperations).set(anyString(), anyLong(), any(Duration.class));

        assertDoesNotThrow(() -> 
            deduplicationService.markAsProcessed(eventId, provider)
        );
    }

    @Test
    void testCheckAndMark_NotDuplicate() {
        when(valueOperations.setIfAbsent(anyString(), anyLong(), any(Duration.class)))
            .thenReturn(true);

        boolean result = deduplicationService.checkAndMark(eventId, provider);

        assertFalse(result);
    }

    @Test
    void testCheckAndMark_IsDuplicate() {
        when(valueOperations.setIfAbsent(anyString(), anyLong(), any(Duration.class)))
            .thenReturn(null);

        boolean result = deduplicationService.checkAndMark(eventId, provider);

        assertTrue(result);
    }

    @Test
    void testCheckAndMark_NullEventId() {
        boolean result = deduplicationService.checkAndMark(null, provider);

        assertTrue(result);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyLong(), any(Duration.class));
    }

    @Test
    void testCheckAndMark_EmptyEventId() {
        boolean result = deduplicationService.checkAndMark("", provider);

        assertTrue(result);
    }

    @Test
    void testCheckAndMark_WithCustomTtl() {
        Duration customTtl = Duration.ofMinutes(20);
        when(valueOperations.setIfAbsent(anyString(), anyLong(), any(Duration.class)))
            .thenReturn(true);

        boolean result = deduplicationService.checkAndMark(eventId, provider, customTtl);

        assertFalse(result);
        verify(valueOperations).setIfAbsent(anyString(), anyLong(), eq(customTtl));
    }

    @Test
    void testCheckAndMark_RedisException() {
        when(valueOperations.setIfAbsent(anyString(), anyLong(), any(Duration.class)))
            .thenThrow(new RuntimeException("Redis error"));

        boolean result = deduplicationService.checkAndMark(eventId, provider);

        // En cas d'erreur Redis, le service retourne false (événement considéré comme nouveau)
        assertFalse(result);
    }

    @Test
    void testRemoveEvent_Success() {
        when(redisTemplate.delete(anyString())).thenReturn(true);

        assertDoesNotThrow(() -> 
            deduplicationService.removeEvent(eventId, provider)
        );

        verify(redisTemplate).delete(anyString());
    }

    @Test
    void testRemoveEvent_NullEventId() {
        assertDoesNotThrow(() -> 
            deduplicationService.removeEvent(null, provider)
        );

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void testRemoveEvent_EmptyEventId() {
        assertDoesNotThrow(() -> 
            deduplicationService.removeEvent("", provider)
        );

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void testRemoveEvent_RedisException() {
        when(redisTemplate.delete(anyString()))
            .thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> 
            deduplicationService.removeEvent(eventId, provider)
        );
    }

    @Test
    void testGetRemainingTtl_Success() {
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS)))
            .thenReturn(600L);

        long result = deduplicationService.getRemainingTtl(eventId, provider);

        assertEquals(600L, result);
    }

    @Test
    void testGetRemainingTtl_NullEventId() {
        long result = deduplicationService.getRemainingTtl(null, provider);

        assertEquals(-1L, result);
        verify(redisTemplate, never()).getExpire(anyString(), any());
    }

    @Test
    void testGetRemainingTtl_EmptyEventId() {
        long result = deduplicationService.getRemainingTtl("", provider);

        assertEquals(-1L, result);
    }

    @Test
    void testGetRemainingTtl_KeyNotExists() {
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS)))
            .thenReturn(-2L);

        long result = deduplicationService.getRemainingTtl(eventId, provider);

        assertEquals(-2L, result);
    }

    @Test
    void testGetRemainingTtl_NoExpiry() {
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS)))
            .thenReturn(-1L);

        long result = deduplicationService.getRemainingTtl(eventId, provider);

        assertEquals(-1L, result);
    }

    @Test
    void testGetRemainingTtl_RedisException() {
        when(redisTemplate.getExpire(anyString(), any()))
            .thenThrow(new RuntimeException("Redis error"));

        long result = deduplicationService.getRemainingTtl(eventId, provider);

        assertEquals(-1L, result);
    }

    @Test
    void testClearProviderEvents_Success() {
        assertDoesNotThrow(() -> 
            deduplicationService.clearProviderEvents(provider)
        );
    }

    @Test
    void testClearProviderEvents_RedisException() {
        assertDoesNotThrow(() -> 
            deduplicationService.clearProviderEvents(provider)
        );
    }

    @Test
    void testBuildDeduplicationKey_Format() {
        // Testing indirectly through isDuplicate
        when(redisTemplate.hasKey("webhook:dedup:github:event-123")).thenReturn(true);

        boolean result = deduplicationService.isDuplicate("event-123", "github");

        assertTrue(result);
    }

    @Test
    void testTtlForProvider_GitHub() {
        deduplicationService.markAsProcessed(eventId, "github");

        verify(valueOperations).set(anyString(), anyLong(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void testTtlForProvider_Slack() {
        deduplicationService.markAsProcessed(eventId, "slack");

        verify(valueOperations).set(anyString(), anyLong(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void testTtlForProvider_Default() {
        deduplicationService.markAsProcessed(eventId, "unknown");

        verify(valueOperations).set(anyString(), anyLong(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void testTtlForProvider_NullProvider() {
        deduplicationService.markAsProcessed(eventId, "unknown");

        verify(valueOperations).set(anyString(), anyLong(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void testMultipleEventsForSameProvider() {
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(false)
            .thenReturn(true);

        boolean result1 = deduplicationService.isDuplicate("event-1", provider);
        boolean result2 = deduplicationService.isDuplicate("event-2", provider);

        assertFalse(result1);
        assertTrue(result2);
    }

    @Test
    void testMultipleProvidersForSameEvent() {
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(false, false);

        boolean result1 = deduplicationService.isDuplicate(eventId, "github");
        boolean result2 = deduplicationService.isDuplicate(eventId, "slack");

        assertFalse(result1);
        assertFalse(result2);
    }

    @Test
    void testCheckAndMark_MultipleThreads() {
        when(valueOperations.setIfAbsent(anyString(), anyLong(), any(Duration.class)))
            .thenReturn(true)
            .thenReturn(null);

        boolean result1 = deduplicationService.checkAndMark(eventId, provider);
        boolean result2 = deduplicationService.checkAndMark(eventId, provider);

        assertFalse(result1);
        assertTrue(result2);
    }

    @Test
    void testProviderCaseInsensitivity() {
        deduplicationService.markAsProcessed(eventId, "GITHUB");
        deduplicationService.markAsProcessed(eventId, "GitHub");
        deduplicationService.markAsProcessed(eventId, "github");

        verify(valueOperations, times(3)).set(anyString(), anyLong(), any(Duration.class));
    }

    @Test
    void testCustomTtl_ZeroDuration() {
        Duration zeroDuration = Duration.ZERO;

        assertDoesNotThrow(() -> 
            deduplicationService.markAsProcessed(eventId, provider, zeroDuration)
        );

        verify(valueOperations).set(anyString(), anyLong(), eq(zeroDuration));
    }

    @Test
    void testCustomTtl_VeryLongDuration() {
        Duration longDuration = Duration.ofDays(365);

        assertDoesNotThrow(() -> 
            deduplicationService.markAsProcessed(eventId, provider, longDuration)
        );

        verify(valueOperations).set(anyString(), anyLong(), eq(longDuration));
    }
}
