package area.server.AREA_Back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Data
@ConfigurationProperties(prefix = "app.redis.stream")
public class RedisStreamProperties {

    private String streamName = "areas:events";
    private String consumerGroup = "area-processors";
    private String consumerName; // Will be set via @PostConstruct if null
    private int batchSize = 10;
    private int threadPoolSize = 4;
    private int pollTimeoutMs = 100;

    public String getConsumerName() {
        if (consumerName == null || consumerName.isEmpty()) {
            consumerName = generateDefaultConsumerName();
        }
        return consumerName;
    }

    private String generateDefaultConsumerName() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            return hostname + "-" + uuid;
        } catch (UnknownHostException e) {
            // Fallback if hostname can't be determined
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            return "area-processor-" + uuid;
        }
    }
}