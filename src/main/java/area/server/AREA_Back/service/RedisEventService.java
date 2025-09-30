package area.server.AREA_Back.service;

import area.server.AREA_Back.config.RedisConfig;
import area.server.AREA_Back.dto.AreaEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisEventService {

    private final RedisTemplate<String, Object> redisTemplate;

    public String publishAreaEvent(AreaEventMessage message) {
        try {
            ObjectRecord<String, AreaEventMessage> record = StreamRecords
                    .newRecord()
                    .in(RedisConfig.AREAS_EVENTS_STREAM)
                    .ofObject(message);
            var recordId = redisTemplate.opsForStream().add(record);
            log.info("Published event to stream {}: executionId={}, actionInstanceId={}, eventType={}",
                    RedisConfig.AREAS_EVENTS_STREAM,
                    message.getExecutionId(),
                    message.getActionInstanceId(),
                    message.getEventType());

            String returnValue;
            if (recordId != null) {
                returnValue = recordId.getValue();
            } else {
                returnValue = "unknown";
            }
            return returnValue;

        } catch (Exception e) {
            log.error("Failed to publish event to Redis stream: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish event to Redis stream", e);
        }
    }

    public String publishExecutionEvent(UUID executionId, UUID actionInstanceId,
                                      UUID areaId, Map<String, Object> payload) {
        AreaEventMessage message = AreaEventMessage.fromExecution(
                executionId, actionInstanceId, areaId, payload);
        return publishAreaEvent(message);
    }

    public void initializeStream() {
        try {
            try {
                redisTemplate.opsForStream().info(RedisConfig.AREAS_EVENTS_STREAM);
            } catch (Exception e) {
                log.info("Creating Redis stream: {}", RedisConfig.AREAS_EVENTS_STREAM);
                var dummyRecord = StreamRecords.string(Map.of("init", "true"))
                        .withStreamKey(RedisConfig.AREAS_EVENTS_STREAM);
                var recordId = redisTemplate.opsForStream().add(dummyRecord);
                redisTemplate.opsForStream().delete(RedisConfig.AREAS_EVENTS_STREAM, recordId);
            }
            try {
                redisTemplate.opsForStream().createGroup(
                        RedisConfig.AREAS_EVENTS_STREAM,
                        ReadOffset.from("0"),
                        RedisConfig.AREAS_CONSUMER_GROUP);
                log.info("Created consumer group: {} for stream: {}",
                        RedisConfig.AREAS_CONSUMER_GROUP,
                        RedisConfig.AREAS_EVENTS_STREAM);
            } catch (Exception e) {
                log.debug("Consumer group {} already exists for stream {}",
                         RedisConfig.AREAS_CONSUMER_GROUP,
                         RedisConfig.AREAS_EVENTS_STREAM);
            }

        } catch (Exception e) {
            log.error("Failed to initialize Redis stream: {}", e.getMessage(), e);
        }
    }

    public Map<String, Object> getStreamInfo() {
        try {
            var info = redisTemplate.opsForStream().info(RedisConfig.AREAS_EVENTS_STREAM);
            String streamInfoValue = info.toString();
            return Map.of(
                "streamKey", RedisConfig.AREAS_EVENTS_STREAM,
                "consumerGroup", RedisConfig.AREAS_CONSUMER_GROUP,
                "streamInfo", streamInfoValue
            );
        } catch (Exception e) {
            log.warn("Failed to get stream info: {}", e.getMessage());
            return Map.of(
                "streamKey", RedisConfig.AREAS_EVENTS_STREAM,
                "consumerGroup", RedisConfig.AREAS_CONSUMER_GROUP,
                "error", e.getMessage()
            );
        }
    }
}