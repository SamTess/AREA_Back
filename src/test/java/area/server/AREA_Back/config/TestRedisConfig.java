package area.server.AREA_Back.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("unit-test")
public class TestRedisConfig {

    @Bean
    @Primary
    public RedisStreamProperties testRedisStreamProperties() {
        RedisStreamProperties properties = new RedisStreamProperties();
        properties.setStreamName("test-areas:events");
        properties.setConsumerGroup("test-area-processors");
        properties.setConsumerName("test-consumer");
        properties.setBatchSize(5);
        properties.setThreadPoolSize(2);
        properties.setPollTimeoutMs(50);
        return properties;
    }
}