package area.server.AREA_Back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminMetricsResponse {
    private Long queueLength;
    private Integer activeWorkers;
    private Integer totalWorkers;
    private Long failedExecutions;
    private Long successfulExecutions;
    private Long totalAreas;
    private Long activeAreas;
    private Long totalUsers;
    private Map<String, Object> systemMetrics;
}
