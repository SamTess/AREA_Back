package area.server.AREA_Back.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerTrackingServiceTest {

    @Mock
    private ThreadPoolTaskExecutor areaWorkerExecutor;

    @Mock
    private ThreadPoolTaskExecutor reactionTaskExecutor;

    @Mock
    private ThreadPoolExecutor areaThreadPoolExecutor;

    @Mock
    private ThreadPoolExecutor reactionThreadPoolExecutor;

    private WorkerTrackingService workerTrackingService;

    private LinkedBlockingQueue<Runnable> areaQueue;
    private LinkedBlockingQueue<Runnable> reactionQueue;

    @BeforeEach
    void setUp() {
        // Mock queue for area worker
        areaQueue = new LinkedBlockingQueue<>(100);
        org.mockito.Mockito.lenient().when(areaThreadPoolExecutor.getQueue()).thenReturn(areaQueue);
        org.mockito.Mockito.lenient().when(areaWorkerExecutor.getThreadPoolExecutor())
                .thenReturn(areaThreadPoolExecutor);

        // Mock queue for reaction worker
        reactionQueue = new LinkedBlockingQueue<>(50);
        org.mockito.Mockito.lenient().when(reactionThreadPoolExecutor.getQueue()).thenReturn(reactionQueue);
        org.mockito.Mockito.lenient().when(reactionTaskExecutor.getThreadPoolExecutor())
                .thenReturn(reactionThreadPoolExecutor);

        workerTrackingService = new WorkerTrackingService(areaWorkerExecutor, reactionTaskExecutor);
    }

    @Test
    void getActiveWorkersShouldReturnSumOfActiveWorkers() {
        // Given
        when(areaWorkerExecutor.getActiveCount()).thenReturn(3);
        when(reactionTaskExecutor.getActiveCount()).thenReturn(2);

        // When
        int activeWorkers = workerTrackingService.getActiveWorkers();

        // Then
        assertThat(activeWorkers).isEqualTo(5);
    }

    @Test
    void getTotalWorkersShouldReturnSumOfTotalWorkers() {
        // Given
        when(areaWorkerExecutor.getPoolSize()).thenReturn(4);
        when(reactionTaskExecutor.getPoolSize()).thenReturn(2);

        // When
        int totalWorkers = workerTrackingService.getTotalWorkers();

        // Then
        assertThat(totalWorkers).isEqualTo(6);
    }

    @Test
    void getMaxWorkersShouldReturnSumOfMaxWorkers() {
        // Given
        when(areaWorkerExecutor.getMaxPoolSize()).thenReturn(10);
        when(reactionTaskExecutor.getMaxPoolSize()).thenReturn(6);

        // When
        int maxWorkers = workerTrackingService.getMaxWorkers();

        // Then
        assertThat(maxWorkers).isEqualTo(16);
    }

    @Test
    void getWorkerStatisticsShouldReturnDetailedStatistics() {
        // Given
        when(areaWorkerExecutor.getActiveCount()).thenReturn(3);
        when(areaWorkerExecutor.getPoolSize()).thenReturn(4);
        when(areaWorkerExecutor.getCorePoolSize()).thenReturn(4);
        when(areaWorkerExecutor.getMaxPoolSize()).thenReturn(10);
        when(areaThreadPoolExecutor.getCompletedTaskCount()).thenReturn(100L);

        when(reactionTaskExecutor.getActiveCount()).thenReturn(2);
        when(reactionTaskExecutor.getPoolSize()).thenReturn(2);
        when(reactionTaskExecutor.getCorePoolSize()).thenReturn(2);
        when(reactionTaskExecutor.getMaxPoolSize()).thenReturn(6);
        when(reactionThreadPoolExecutor.getCompletedTaskCount()).thenReturn(50L);

        // When
        Map<String, Object> statistics = workerTrackingService.getWorkerStatistics();

        // Then
        assertThat(statistics).containsKeys(
            "activeWorkers", "totalWorkers", "maxWorkers", 
            "areaWorker", "reactionWorker"
        );
        assertThat(statistics.get("activeWorkers")).isEqualTo(5);
        assertThat(statistics.get("totalWorkers")).isEqualTo(6);
        assertThat(statistics.get("maxWorkers")).isEqualTo(16);

        @SuppressWarnings("unchecked")
        Map<String, Object> areaWorkerStats = (Map<String, Object>) statistics.get("areaWorker");
        assertThat(areaWorkerStats.get("active")).isEqualTo(3);
        assertThat(areaWorkerStats.get("poolSize")).isEqualTo(4);
        assertThat(areaWorkerStats.get("completedTaskCount")).isEqualTo(100L);

        @SuppressWarnings("unchecked")
        Map<String, Object> reactionWorkerStats = (Map<String, Object>) statistics.get("reactionWorker");
        assertThat(reactionWorkerStats.get("active")).isEqualTo(2);
        assertThat(reactionWorkerStats.get("poolSize")).isEqualTo(2);
        assertThat(reactionWorkerStats.get("completedTaskCount")).isEqualTo(50L);
    }

    @Test
    void isHealthyShouldReturnTrueWhenQueuesAreNotOverloaded() {
        // Given - queues are empty (< 80% full)
        // Queue capacity is set in setUp (100 for area, 50 for reaction)

        // When
        boolean healthy = workerTrackingService.isHealthy();

        // Then
        assertThat(healthy).isTrue();
    }

    @Test
    void getHealthStatusShouldReturnHealthStatusDetails() {
        // Given
        when(areaWorkerExecutor.getActiveCount()).thenReturn(3);
        when(reactionTaskExecutor.getActiveCount()).thenReturn(2);
        when(areaWorkerExecutor.getPoolSize()).thenReturn(4);
        when(reactionTaskExecutor.getPoolSize()).thenReturn(2);
        when(areaWorkerExecutor.getMaxPoolSize()).thenReturn(10);
        when(reactionTaskExecutor.getMaxPoolSize()).thenReturn(6);

        // When
        Map<String, Object> healthStatus = workerTrackingService.getHealthStatus();

        // Then
        assertThat(healthStatus).containsKeys(
            "healthy", "activeWorkers", "totalWorkers", "maxWorkers",
            "areaWorkerQueueSize", "reactionWorkerQueueSize"
        );
        assertThat(healthStatus.get("healthy")).isEqualTo(true);
        assertThat(healthStatus.get("activeWorkers")).isEqualTo(5);
        assertThat(healthStatus.get("totalWorkers")).isEqualTo(6);
        assertThat(healthStatus.get("maxWorkers")).isEqualTo(16);
    }
}
