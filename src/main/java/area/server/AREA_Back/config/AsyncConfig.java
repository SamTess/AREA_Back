package area.server.AREA_Back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private static final int WORKER_CORE_POOL_SIZE = 4;
    private static final int WORKER_MAX_POOL_SIZE = 10;
    private static final int WORKER_QUEUE_CAPACITY = 100;
    private static final int WORKER_AWAIT_TERMINATION_SECONDS = 30;
    private static final int REACTION_CORE_POOL_SIZE = 2;
    private static final int REACTION_MAX_POOL_SIZE = 6;
    private static final int REACTION_QUEUE_CAPACITY = 50;
    private static final int REACTION_AWAIT_TERMINATION_SECONDS = 20;

    @Bean(name = "areaWorkerExecutor")
    public Executor areaWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(WORKER_CORE_POOL_SIZE);
        executor.setMaxPoolSize(WORKER_MAX_POOL_SIZE);
        executor.setQueueCapacity(WORKER_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("AreaWorker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(WORKER_AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    @Bean(name = "reactionTaskExecutor")
    public Executor reactionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(REACTION_CORE_POOL_SIZE);
        executor.setMaxPoolSize(REACTION_MAX_POOL_SIZE);
        executor.setQueueCapacity(REACTION_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("ReactionExec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(REACTION_AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("CRON-Scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}