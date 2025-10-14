package area.server.AREA_Back.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@EnableRedisRepositories
@Profile("!unit-test")
@Slf4j
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisStreamProperties streamProperties;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    private static final int DEFAULT_TTL_MINUTES = 30;
    private static final int TOKEN_TTL_MINUTES = 15;
    private static final int SESSION_TTL_MINUTES = 20;
    private static final int SERVICES_CATALOG_TTL_HOURS = 1;

    public String getAreasEventsStream() {
        return streamProperties.getStreamName();
    }

    public String getAreasConsumerGroup() {
        return streamProperties.getConsumerGroup();
    }

    public String getAreasConsumerName() {
        return streamProperties.getConsumerName();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection - Host: {}, Port: {}, SSL: {}",
                 redisHost, redisPort, sslEnabled);

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setDatabase(redisDatabase);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder =
            LettuceClientConfiguration.builder();

        if (sslEnabled) {
            log.info("Enabling TLS/SSL for Redis connection");
            SslOptions sslOptions = SslOptions.builder()
                .jdkSslProvider()
                .build();

            ClientOptions clientOptions = ClientOptions.builder()
                .sslOptions(sslOptions)
                .build();

            clientConfigBuilder
                .useSsl()
                .disablePeerVerification()  // !! Big security flaw, replace in production
                .and()                      // with letsencrypt or a valid certificate
                .clientOptions(clientOptions);
        } else {
            log.warn(
                "TLS/SSL is DISABLED for Redis. "
                + "Enable it in production by setting REDIS_SSL=true"
            );
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(
            redisConfig,
            clientConfigBuilder.build()
        );

        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(DEFAULT_TTL_MINUTES))
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext
                        .SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext
                        .SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put("services", defaultConfig
                .entryTtl(Duration.ofHours(1)));

        cacheConfigurations.put("services-catalog", defaultConfig
                .entryTtl(Duration.ofHours(SERVICES_CATALOG_TTL_HOURS)));

        cacheConfigurations.put("actionDefinitions", defaultConfig
                .entryTtl(Duration.ofMinutes(DEFAULT_TTL_MINUTES)));

        cacheConfigurations.put("tokens", defaultConfig
                .entryTtl(Duration.ofMinutes(TOKEN_TTL_MINUTES)));

        cacheConfigurations.put("rateLimits", defaultConfig
                .entryTtl(Duration.ofMinutes(1)));

        cacheConfigurations.put("userSessions", defaultConfig
                .entryTtl(Duration.ofMinutes(SESSION_TTL_MINUTES)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    @Bean
    public StreamMessageListenerContainer<String, ?> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        log.info("Configuring Redis Stream Listener with consumer name: { }", streamProperties.getConsumerName());

        StreamMessageListenerContainerOptions<String, ?> options = StreamMessageListenerContainerOptions
                .builder()
                .batchSize(streamProperties.getBatchSize())
                .executor(java.util.concurrent.Executors.newFixedThreadPool(streamProperties.getThreadPoolSize()))
                .pollTimeout(Duration.ofMillis(streamProperties.getPollTimeoutMs()))
                .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }
}