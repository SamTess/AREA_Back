package area.server.AREA_Back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Data
@ConfigurationProperties(prefix = "app.redis.stream")
public class RedisStreamProperties {

    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    private static final int DEFAULT_POLL_TIMEOUT_MS = 100;
    private static final int UUID_SUBSTRING_LENGTH = 8;

    private String streamName = "areas:events";
    private String consumerGroup = "area-processors";
    private String consumerName; // Will be set via @PostConstruct if null
    private int batchSize = DEFAULT_BATCH_SIZE;
    private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
    private int pollTimeoutMs = DEFAULT_POLL_TIMEOUT_MS;

    public String getConsumerName() {
        if (consumerName == null || consumerName.isEmpty()) {
            consumerName = generateDefaultConsumerName();
        }
        return consumerName;
    }

    private String generateDefaultConsumerName() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String uuid = UUID.randomUUID().toString().substring(0, UUID_SUBSTRING_LENGTH);
            return hostname + "-" + uuid;
        } catch (UnknownHostException e) {
            // Fallback if hostname can't be determined
            String uuid = UUID.randomUUID().toString().substring(0, UUID_SUBSTRING_LENGTH);
            return "area-processor-" + uuid;
        }
    }
}