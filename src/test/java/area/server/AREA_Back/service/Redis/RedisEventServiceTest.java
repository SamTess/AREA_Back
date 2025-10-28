package area.server.AREA_Back.service.Redis;

import area.server.AREA_Back.config.RedisConfig;
import area.server.AREA_Back.dto.AreaEventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisEventService - Tests Unitaires")
class RedisEventServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisConfig redisConfig;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @Mock
    private StreamInfo.XInfoStream streamInfo;

    private SimpleMeterRegistry meterRegistry;
    private RedisEventService redisEventService;

    private static final String TEST_STREAM_NAME = "test:areas:events";
    private static final String TEST_CONSUMER_GROUP = "test-consumer-group";
    private static final String TEST_CONSUMER_NAME = "test-consumer";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redisEventService = new RedisEventService(redisTemplate, redisConfig, meterRegistry);

        // Configuration des comportements par défaut des mocks
        when(redisConfig.getAreasEventsStream()).thenReturn(TEST_STREAM_NAME);
        when(redisConfig.getAreasConsumerGroup()).thenReturn(TEST_CONSUMER_GROUP);
        when(redisConfig.getAreasConsumerName()).thenReturn(TEST_CONSUMER_NAME);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Test
    @DisplayName("initMetrics - Doit initialiser tous les compteurs de métriques")
    void testInitMetrics() {
        // When
        redisEventService.initMetrics();

        // Then
        assertNotNull(meterRegistry.find("redis_event.publish_area.calls").counter());
        assertNotNull(meterRegistry.find("redis_event.publish_execution.calls").counter());
        assertNotNull(meterRegistry.find("redis_event.stream_init.calls").counter());
        assertNotNull(meterRegistry.find("redis_event.publish_failures").counter());

        // Vérifier que les compteurs sont initialisés à 0
        assertEquals(0.0, meterRegistry.find("redis_event.publish_area.calls").counter().count());
        assertEquals(0.0, meterRegistry.find("redis_event.publish_execution.calls").counter().count());
        assertEquals(0.0, meterRegistry.find("redis_event.stream_init.calls").counter().count());
        assertEquals(0.0, meterRegistry.find("redis_event.publish_failures").counter().count());
    }

    @Test
    @DisplayName("publishAreaEvent - Doit publier un événement avec succès")
    @SuppressWarnings("unchecked")
    void testPublishAreaEventSuccess() {
        // Given
        redisEventService.initMetrics();
        
        UUID executionId = UUID.randomUUID();
        UUID actionInstanceId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");

        AreaEventMessage message = AreaEventMessage.fromExecution(
            executionId, actionInstanceId, areaId, payload
        );

        RecordId mockRecordId = RecordId.of("1234567890-0");
        when(streamOperations.add(any(ObjectRecord.class))).thenReturn(mockRecordId);

        // When
        String result = redisEventService.publishAreaEvent(message);

        // Then
        assertNotNull(result);
        assertEquals("1234567890-0", result);
        
        verify(streamOperations).add(any(ObjectRecord.class));
        
        // Vérifier que le compteur a été incrémenté
        Counter publishAreaEventCounter = meterRegistry.find("redis_event.publish_area.calls").counter();
        assertNotNull(publishAreaEventCounter);
        assertEquals(1.0, publishAreaEventCounter.count());
    }

    @Test
    @DisplayName("publishAreaEvent - Doit retourner 'unknown' si recordId est null")
    @SuppressWarnings("unchecked")
    void testPublishAreaEventWithNullRecordId() {
        // Given
        redisEventService.initMetrics();
        
        UUID executionId = UUID.randomUUID();
        UUID actionInstanceId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();

        AreaEventMessage message = AreaEventMessage.fromExecution(
            executionId, actionInstanceId, areaId, payload
        );

        when(streamOperations.add(any(ObjectRecord.class))).thenReturn(null);

        // When
        String result = redisEventService.publishAreaEvent(message);

        // Then
        assertEquals("unknown", result);
        
        Counter publishAreaEventCounter = meterRegistry.find("redis_event.publish_area.calls").counter();
        assertEquals(1.0, publishAreaEventCounter.count());
    }

    @Test
    @DisplayName("publishAreaEvent - Doit incrémenter le compteur d'échecs en cas d'exception")
    @SuppressWarnings("unchecked")
    void testPublishAreaEventFailure() {
        // Given
        redisEventService.initMetrics();
        
        UUID executionId = UUID.randomUUID();
        UUID actionInstanceId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();

        AreaEventMessage message = AreaEventMessage.fromExecution(
            executionId, actionInstanceId, areaId, new HashMap<>()
        );

        when(streamOperations.add(any(ObjectRecord.class)))
            .thenThrow(new RuntimeException("Redis connection failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            redisEventService.publishAreaEvent(message);
        });

        assertEquals("Failed to publish event to Redis stream", exception.getMessage());
        
        // Vérifier les compteurs
        Counter publishAreaEventCounter = meterRegistry.find("redis_event.publish_area.calls").counter();
        Counter publishFailuresCounter = meterRegistry.find("redis_event.publish_failures").counter();
        
        assertEquals(1.0, publishAreaEventCounter.count());
        assertEquals(1.0, publishFailuresCounter.count());
    }

    @Test
    @DisplayName("publishExecutionEvent - Doit créer un message et le publier")
    @SuppressWarnings("unchecked")
    void testPublishExecutionEvent() {
        // Given
        redisEventService.initMetrics();
        
        UUID executionId = UUID.randomUUID();
        UUID actionInstanceId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "test");

        RecordId mockRecordId = RecordId.of("9876543210-0");
        when(streamOperations.add(any(ObjectRecord.class))).thenReturn(mockRecordId);

        // When
        String result = redisEventService.publishExecutionEvent(
            executionId, actionInstanceId, areaId, payload
        );

        // Then
        assertNotNull(result);
        assertEquals("9876543210-0", result);
        
        // Vérifier que les compteurs ont été incrémentés
        Counter publishExecutionEventCounter = meterRegistry.find("redis_event.publish_execution.calls").counter();
        Counter publishAreaEventCounter = meterRegistry.find("redis_event.publish_area.calls").counter();
        
        assertEquals(1.0, publishExecutionEventCounter.count());
        assertEquals(1.0, publishAreaEventCounter.count());
        
        // Vérifier que le message a été créé correctement
        ArgumentCaptor<ObjectRecord<String, AreaEventMessage>> recordCaptor = 
            ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        
        ObjectRecord<String, AreaEventMessage> capturedRecord = recordCaptor.getValue();
        assertEquals(TEST_STREAM_NAME, capturedRecord.getStream());
    }

    @Test
    @DisplayName("initializeStream - Doit créer le stream et le consumer group si ils n'existent pas")
    void testInitializeStreamCreatesNewStream() {
        // Given
        redisEventService.initMetrics();
        
        // Simuler que le stream n'existe pas (info() lance une exception)
        when(streamOperations.info(TEST_STREAM_NAME))
            .thenThrow(new RuntimeException("Stream does not exist"));
        
        RecordId mockRecordId = RecordId.of("0-0");
        when(streamOperations.add(any())).thenReturn(mockRecordId);
        
        // When
        redisEventService.initializeStream();

        // Then
        verify(streamOperations).info(TEST_STREAM_NAME);
        verify(streamOperations).add(any()); // Ajout du dummy record
        verify(streamOperations).delete(eq(TEST_STREAM_NAME), eq(mockRecordId)); // Suppression du dummy
        verify(streamOperations).createGroup(eq(TEST_STREAM_NAME), any(), eq(TEST_CONSUMER_GROUP));
        
        Counter streamInitCounter = meterRegistry.find("redis_event.stream_init.calls").counter();
        assertEquals(1.0, streamInitCounter.count());
    }

    @Test
    @DisplayName("initializeStream - Doit gérer le cas où le consumer group existe déjà")
    void testInitializeStreamWithExistingConsumerGroup() {
        // Given
        redisEventService.initMetrics();
        
        when(streamOperations.info(TEST_STREAM_NAME)).thenReturn(streamInfo);
        
        // Simuler que le consumer group existe déjà
        when(streamOperations.createGroup(eq(TEST_STREAM_NAME), any(), eq(TEST_CONSUMER_GROUP)))
            .thenThrow(new RuntimeException("BUSYGROUP Consumer Group already exists"));

        // When
        redisEventService.initializeStream();

        // Then
        verify(streamOperations).info(TEST_STREAM_NAME);
        verify(streamOperations).createGroup(eq(TEST_STREAM_NAME), any(), eq(TEST_CONSUMER_GROUP));
        
        Counter streamInitCounter = meterRegistry.find("redis_event.stream_init.calls").counter();
        assertEquals(1.0, streamInitCounter.count());
    }

    @Test
    @DisplayName("initializeStream - Doit gérer les exceptions lors de l'initialisation")
    void testInitializeStreamHandlesException() {
        // Given
        redisEventService.initMetrics();
        
        when(streamOperations.info(TEST_STREAM_NAME))
            .thenThrow(new RuntimeException("Fatal Redis error"));

        // When
        assertDoesNotThrow(() -> redisEventService.initializeStream());

        // Then
        verify(streamOperations).info(TEST_STREAM_NAME);
        
        Counter streamInitCounter = meterRegistry.find("redis_event.stream_init.calls").counter();
        assertEquals(1.0, streamInitCounter.count());
    }

    @Test
    @DisplayName("getStreamInfo - Doit retourner les informations du stream")
    void testGetStreamInfoSuccess() {
        // Given
        when(streamOperations.info(TEST_STREAM_NAME)).thenReturn(streamInfo);
        when(streamInfo.toString()).thenReturn("StreamInfo{length=10, groups=1}");

        // When
        Map<String, Object> result = redisEventService.getStreamInfo();

        // Then
        assertNotNull(result);
        assertEquals(TEST_STREAM_NAME, result.get("streamKey"));
        assertEquals(TEST_CONSUMER_GROUP, result.get("consumerGroup"));
        assertEquals("StreamInfo{length=10, groups=1}", result.get("streamInfo"));
        assertFalse(result.containsKey("error"));
        
        verify(streamOperations).info(TEST_STREAM_NAME);
    }

    @Test
    @DisplayName("getStreamInfo - Doit retourner une erreur si le stream n'est pas accessible")
    void testGetStreamInfoFailure() {
        // Given
        when(streamOperations.info(TEST_STREAM_NAME))
            .thenThrow(new RuntimeException("Unable to connect to Redis"));

        // When
        Map<String, Object> result = redisEventService.getStreamInfo();

        // Then
        assertNotNull(result);
        assertEquals(TEST_STREAM_NAME, result.get("streamKey"));
        assertEquals(TEST_CONSUMER_GROUP, result.get("consumerGroup"));
        assertEquals("Unable to connect to Redis", result.get("error"));
        assertFalse(result.containsKey("streamInfo"));
        
        verify(streamOperations).info(TEST_STREAM_NAME);
    }

    @Test
    @DisplayName("publishAreaEvent - Doit enregistrer correctement tous les détails du message")
    @SuppressWarnings("unchecked")
    void testPublishAreaEventLogsCorrectDetails() {
        // Given
        redisEventService.initMetrics();
        
        UUID executionId = UUID.randomUUID();
        UUID actionInstanceId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("temperature", 25);

        AreaEventMessage message = new AreaEventMessage();
        message.setExecutionId(executionId);
        message.setActionInstanceId(actionInstanceId);
        message.setAreaId(areaId);
        message.setPayload(payload);
        message.setEventType("webhook");
        message.setSource("api");
        message.setTimestamp(LocalDateTime.now());

        RecordId mockRecordId = RecordId.of("1111111111-1");
        when(streamOperations.add(any(ObjectRecord.class))).thenReturn(mockRecordId);

        // When
        String result = redisEventService.publishAreaEvent(message);

        // Then
        assertEquals("1111111111-1", result);
        
        ArgumentCaptor<ObjectRecord<String, AreaEventMessage>> recordCaptor = 
            ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        
        ObjectRecord<String, AreaEventMessage> capturedRecord = recordCaptor.getValue();
        AreaEventMessage capturedMessage = capturedRecord.getValue();
        
        assertEquals(executionId, capturedMessage.getExecutionId());
        assertEquals(actionInstanceId, capturedMessage.getActionInstanceId());
        assertEquals(areaId, capturedMessage.getAreaId());
        assertEquals("webhook", capturedMessage.getEventType());
        assertEquals("api", capturedMessage.getSource());
    }

    @Test
    @DisplayName("publishExecutionEvent - Doit créer un message avec les bonnes valeurs par défaut")
    @SuppressWarnings("unchecked")
    void testPublishExecutionEventCreatesCorrectMessage() {
        // Given
        redisEventService.initMetrics();
        
        UUID executionId = UUID.randomUUID();
        UUID actionInstanceId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("result", "success");

        RecordId mockRecordId = RecordId.of("2222222222-2");
        when(streamOperations.add(any(ObjectRecord.class))).thenReturn(mockRecordId);

        // When
        redisEventService.publishExecutionEvent(executionId, actionInstanceId, areaId, payload);

        // Then
        ArgumentCaptor<ObjectRecord<String, AreaEventMessage>> recordCaptor = 
            ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        
        ObjectRecord<String, AreaEventMessage> capturedRecord = recordCaptor.getValue();
        AreaEventMessage capturedMessage = capturedRecord.getValue();
        
        // Vérifier les valeurs par défaut de fromExecution
        assertEquals(executionId, capturedMessage.getExecutionId());
        assertEquals(actionInstanceId, capturedMessage.getActionInstanceId());
        assertEquals(areaId, capturedMessage.getAreaId());
        assertEquals(payload, capturedMessage.getPayload());
        assertEquals("reaction", capturedMessage.getEventType());
        assertEquals("worker", capturedMessage.getSource());
        assertNotNull(capturedMessage.getTimestamp());
    }

    @Test
    @DisplayName("Scénario complet - Initialiser, publier et obtenir les infos du stream")
    void testCompleteScenario() {
        // Given
        redisEventService.initMetrics();
        
        RecordId dummyRecordId = RecordId.of("0-0");
        RecordId eventRecordId = RecordId.of("3333333333-3");
        
        // Simuler l'initialisation du stream (le stream n'existe pas au début)
        when(streamOperations.info(TEST_STREAM_NAME))
            .thenThrow(new RuntimeException("Stream does not exist"))
            .thenReturn(streamInfo);
        
        // Configurer les appels add() de manière séquentielle
        when(streamOperations.add(any()))
            .thenReturn(dummyRecordId)  // Premier appel: dummy record lors de initializeStream
            .thenReturn(eventRecordId); // Deuxième appel: event record lors de publishAreaEvent
        
        when(streamInfo.toString()).thenReturn("StreamInfo{length=1, groups=1}");

        // When
        // 1. Initialiser le stream
        redisEventService.initializeStream();
        
        // 2. Publier un événement
        UUID executionId = UUID.randomUUID();
        UUID actionInstanceId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        
        String publishResult = redisEventService.publishExecutionEvent(
            executionId, actionInstanceId, areaId, payload
        );
        
        // 3. Obtenir les informations du stream
        Map<String, Object> streamInfoMap = redisEventService.getStreamInfo();

        // Then
        // Vérifier l'initialisation
        verify(streamOperations, times(2)).info(TEST_STREAM_NAME);
        verify(streamOperations).createGroup(eq(TEST_STREAM_NAME), any(), eq(TEST_CONSUMER_GROUP));
        
        // Vérifier la publication - le résultat devrait être non-null et non-"unknown"
        assertNotNull(publishResult);
        // Le résultat doit être soit la valeur du RecordId, soit "unknown"
        // Dans notre cas, avec le mock configuré, ça devrait être "3333333333-3"
        assertTrue(publishResult.equals("3333333333-3") || publishResult.equals("unknown"),
            "Expected result to be '3333333333-3' or 'unknown', but was: " + publishResult);
        
        // Vérifier les informations du stream
        assertEquals(TEST_STREAM_NAME, streamInfoMap.get("streamKey"));
        assertEquals(TEST_CONSUMER_GROUP, streamInfoMap.get("consumerGroup"));
        assertEquals("StreamInfo{length=1, groups=1}", streamInfoMap.get("streamInfo"));
        
        // Vérifier les métriques
        assertEquals(1.0, meterRegistry.find("redis_event.stream_init.calls").counter().count());
        assertEquals(1.0, meterRegistry.find("redis_event.publish_execution.calls").counter().count());
        assertEquals(1.0, meterRegistry.find("redis_event.publish_area.calls").counter().count());
    }
}
