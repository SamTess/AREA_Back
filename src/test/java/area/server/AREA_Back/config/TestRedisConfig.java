package area.server.AREA_Back.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("unit-test")
public class TestRedisConfig {

    private static final int TEST_BATCH_SIZE = 5;
    private static final int TEST_THREAD_POOL_SIZE = 2;
    private static final int TEST_POLL_TIMEOUT_MS = 50;

    @Bean
    @Primary
    public RedisStreamProperties testRedisStreamProperties() {
        RedisStreamProperties properties = new RedisStreamProperties();
        properties.setStreamName("test-areas:events");
        properties.setConsumerGroup("test-area-processors");
        properties.setConsumerName("test-consumer");
        properties.setBatchSize(TEST_BATCH_SIZE);
        properties.setThreadPoolSize(TEST_THREAD_POOL_SIZE);
        properties.setPollTimeoutMs(TEST_POLL_TIMEOUT_MS);
        return properties;
    }
}