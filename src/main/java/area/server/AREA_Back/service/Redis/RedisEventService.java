package area.server.AREA_Back.service.Redis;

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

import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisEventService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConfig redisConfig;
    private final MeterRegistry meterRegistry;

    private Counter publishAreaEventCalls;
    private Counter publishExecutionEventCalls;
    private Counter streamInitializationCalls;
    private Counter publishFailures;

    @PostConstruct
    public void initMetrics() {
        publishAreaEventCalls = Counter.builder("redis_event.publish_area.calls")
                .description("Total number of area event publish calls")
                .register(meterRegistry);

        publishExecutionEventCalls = Counter.builder("redis_event.publish_execution.calls")
                .description("Total number of execution event publish calls")
                .register(meterRegistry);

        streamInitializationCalls = Counter.builder("redis_event.stream_init.calls")
                .description("Total number of stream initialization calls")
                .register(meterRegistry);

        publishFailures = Counter.builder("redis_event.publish_failures")
                .description("Total number of event publish failures")
                .register(meterRegistry);
    }

    public String publishAreaEvent(AreaEventMessage message) {
        publishAreaEventCalls.increment();
        try {
            ObjectRecord<String, AreaEventMessage> record = StreamRecords
                    .newRecord()
                    .in(redisConfig.getAreasEventsStream())
                    .ofObject(message);
            var recordId = redisTemplate.opsForStream().add(record);
            log.info("Published event to stream { }: executionId={ }, actionInstanceId={ }, eventType={ }",
                    redisConfig.getAreasEventsStream(),
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
            publishFailures.increment();
            log.error("Failed to publish event to Redis stream: { }", e.getMessage(), e);
            throw new RuntimeException("Failed to publish event to Redis stream", e);
        }
    }

    public String publishExecutionEvent(UUID executionId, UUID actionInstanceId,
                                      UUID areaId, Map<String, Object> payload) {
        publishExecutionEventCalls.increment();
        AreaEventMessage message = AreaEventMessage.fromExecution(
                executionId, actionInstanceId, areaId, payload);
        return publishAreaEvent(message);
    }

    public void initializeStream() {
        streamInitializationCalls.increment();
        try {
            try {
                redisTemplate.opsForStream().info(redisConfig.getAreasEventsStream());
            } catch (Exception e) {
                log.info("Creating Redis stream: { }", redisConfig.getAreasEventsStream());
                var dummyRecord = StreamRecords.string(Map.of("init", "true"))
                        .withStreamKey(redisConfig.getAreasEventsStream());
                var recordId = redisTemplate.opsForStream().add(dummyRecord);
                redisTemplate.opsForStream().delete(redisConfig.getAreasEventsStream(), recordId);
            }
            try {
                redisTemplate.opsForStream().createGroup(
                        redisConfig.getAreasEventsStream(),
                        ReadOffset.from("0"),
                        redisConfig.getAreasConsumerGroup());
                log.info("Created consumer group: { } for stream: { }",
                        redisConfig.getAreasConsumerGroup(),
                        redisConfig.getAreasEventsStream());
            } catch (Exception e) {
                log.debug("Consumer group { } already exists for stream { }",
                         redisConfig.getAreasConsumerGroup(),
                         redisConfig.getAreasEventsStream());
            }

        } catch (Exception e) {
            log.error("Failed to initialize Redis stream: { }", e.getMessage(), e);
        }
    }

    public Map<String, Object> getStreamInfo() {
        try {
            var info = redisTemplate.opsForStream().info(redisConfig.getAreasEventsStream());
            String streamInfoValue = info.toString();
            return Map.of(
                "streamKey", redisConfig.getAreasEventsStream(),
                "consumerGroup", redisConfig.getAreasConsumerGroup(),
                "streamInfo", streamInfoValue
            );
        } catch (Exception e) {
            log.warn("Failed to get stream info: { }", e.getMessage());
            return Map.of(
                "streamKey", redisConfig.getAreasEventsStream(),
                "consumerGroup", redisConfig.getAreasConsumerGroup(),
                "error", e.getMessage()
            );
        }
    }
}