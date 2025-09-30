package area.server.AREA_Back.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@Profile("!unit-test")
public class RedisConfig {

    private static final int DEFAULT_TTL_MINUTES = 30;
    private static final int TOKEN_TTL_MINUTES = 15;
    private static final int SESSION_TTL_MINUTES = 20;
    private static final int SERVICES_CATALOG_TTL_HOURS = 1;

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
}