package area.server.AREA_Back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class WorkerTrackingService {

    private final ThreadPoolTaskExecutor areaWorkerExecutor;
    private final ThreadPoolTaskExecutor reactionTaskExecutor;

    public WorkerTrackingService(
            @Qualifier("areaWorkerExecutor") Executor areaWorkerExecutor,
            @Qualifier("reactionTaskExecutor") Executor reactionTaskExecutor) {
        this.areaWorkerExecutor = (ThreadPoolTaskExecutor) areaWorkerExecutor;
        this.reactionTaskExecutor = (ThreadPoolTaskExecutor) reactionTaskExecutor;
        log.info("WorkerTrackingService initialized");
    }

    public int getActiveWorkers() {
        int areaWorkerActive = areaWorkerExecutor.getActiveCount();
        int reactionWorkerActive = reactionTaskExecutor.getActiveCount();
        return areaWorkerActive + reactionWorkerActive;
    }

    public int getTotalWorkers() {
        int areaWorkerTotal = areaWorkerExecutor.getPoolSize();
        int reactionWorkerTotal = reactionTaskExecutor.getPoolSize();
        return areaWorkerTotal + reactionWorkerTotal;
    }

    public int getMaxWorkers() {
        int areaWorkerMax = areaWorkerExecutor.getMaxPoolSize();
        int reactionWorkerMax = reactionTaskExecutor.getMaxPoolSize();
        return areaWorkerMax + reactionWorkerMax;
    }

    public Map<String, Object> getWorkerStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("activeWorkers", getActiveWorkers());
        stats.put("totalWorkers", getTotalWorkers());
        stats.put("maxWorkers", getMaxWorkers());

        Map<String, Object> areaWorkerStats = new HashMap<>();
        areaWorkerStats.put("active", areaWorkerExecutor.getActiveCount());
        areaWorkerStats.put("poolSize", areaWorkerExecutor.getPoolSize());
        areaWorkerStats.put("corePoolSize", areaWorkerExecutor.getCorePoolSize());
        areaWorkerStats.put("maxPoolSize", areaWorkerExecutor.getMaxPoolSize());
        areaWorkerStats.put("queueSize", areaWorkerExecutor.getThreadPoolExecutor().getQueue().size());
        int areaQueueRemaining = areaWorkerExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        int areaQueueSize = areaWorkerExecutor.getThreadPoolExecutor().getQueue().size();
        areaWorkerStats.put("queueCapacity", areaQueueRemaining + areaQueueSize);
        long areaCompletedTasks = areaWorkerExecutor.getThreadPoolExecutor().getCompletedTaskCount();
        areaWorkerStats.put("completedTaskCount", areaCompletedTasks);
        stats.put("areaWorker", areaWorkerStats);

        Map<String, Object> reactionWorkerStats = new HashMap<>();
        reactionWorkerStats.put("active", reactionTaskExecutor.getActiveCount());
        reactionWorkerStats.put("poolSize", reactionTaskExecutor.getPoolSize());
        reactionWorkerStats.put("corePoolSize", reactionTaskExecutor.getCorePoolSize());
        reactionWorkerStats.put("maxPoolSize", reactionTaskExecutor.getMaxPoolSize());
        reactionWorkerStats.put("queueSize", reactionTaskExecutor.getThreadPoolExecutor().getQueue().size());
        int reactionQueueRemaining = reactionTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        int reactionQueueSize = reactionTaskExecutor.getThreadPoolExecutor().getQueue().size();
        reactionWorkerStats.put("queueCapacity", reactionQueueRemaining + reactionQueueSize);
        long reactionCompletedTasks = reactionTaskExecutor.getThreadPoolExecutor().getCompletedTaskCount();
        reactionWorkerStats.put("completedTaskCount", reactionCompletedTasks);
        stats.put("reactionWorker", reactionWorkerStats);

        return stats;
    }

    public boolean isHealthy() {
        final double healthThreshold = 0.8;
        int areaWorkerQueueSize = areaWorkerExecutor.getThreadPoolExecutor().getQueue().size();
        int reactionWorkerQueueSize = reactionTaskExecutor.getThreadPoolExecutor().getQueue().size();

        int areaWorkerQueueRemaining = areaWorkerExecutor.getThreadPoolExecutor().getQueue()
                .remainingCapacity();
        int areaWorkerQueueCapacity = areaWorkerQueueRemaining + areaWorkerQueueSize;
        int reactionWorkerQueueRemaining = reactionTaskExecutor.getThreadPoolExecutor().getQueue()
                .remainingCapacity();
        int reactionWorkerQueueCapacity = reactionWorkerQueueRemaining + reactionWorkerQueueSize;

        boolean areaWorkerHealthy = areaWorkerQueueSize < (areaWorkerQueueCapacity * healthThreshold);
        boolean reactionWorkerHealthy = reactionWorkerQueueSize < (reactionWorkerQueueCapacity
                * healthThreshold);

        return areaWorkerHealthy && reactionWorkerHealthy;
    }

    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        health.put("healthy", isHealthy());
        health.put("activeWorkers", getActiveWorkers());
        health.put("totalWorkers", getTotalWorkers());
        health.put("maxWorkers", getMaxWorkers());

        int areaWorkerQueueSize = areaWorkerExecutor.getThreadPoolExecutor().getQueue().size();
        int reactionWorkerQueueSize = reactionTaskExecutor.getThreadPoolExecutor().getQueue().size();

        health.put("areaWorkerQueueSize", areaWorkerQueueSize);
        health.put("reactionWorkerQueueSize", reactionWorkerQueueSize);

        return health;
    }
}
