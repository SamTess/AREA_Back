package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaEventMessage {
    private UUID actionInstanceId;
    private UUID areaId;
    private UUID executionId;
    private String eventType;  // "webhook", "poll", "cron", "manual", "chain"
    private Map<String, Object> payload;
    private UUID correlationId;
    private String source;  // "webhook", "poller", "scheduler", "api"
    private LocalDateTime timestamp;
    private Map<String, String> metadata;
    private Integer priority = 0;  // 0 = normal, higher = more priority

    public static AreaEventMessage fromExecution(final UUID executionId, final UUID actionInstanceId,
                                                 final UUID areaId, final Map<String, Object> payload) {
        AreaEventMessage message = new AreaEventMessage();
        message.setExecutionId(executionId);
        message.setActionInstanceId(actionInstanceId);
        message.setAreaId(areaId);
        message.setPayload(payload);
        message.setTimestamp(LocalDateTime.now());
        message.setEventType("reaction");
        message.setSource("worker");
        return message;
    }
}