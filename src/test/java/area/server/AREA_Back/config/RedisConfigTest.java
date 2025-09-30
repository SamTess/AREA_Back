package area.server.AREA_Back.config;

import area.server.AREA_Back.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("redis-test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "spring.cache.type=redis",
    "spring.main.allow-bean-definition-overriding=true"
})
class RedisConfigTest {

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Test
    void testRedisCacheManagerIsConfigured() {
        assertNotNull(cacheManager, "CacheManager should not be null");
        assertTrue(cacheManager instanceof RedisCacheManager, "CacheManager should be RedisCacheManager");
    }

    @Test
    void testServicesCatalogCacheIsConfigured() {
        assertNotNull(cacheManager);
        assertNotNull(cacheManager.getCache("services-catalog"), "services-catalog cache should exist");
    }

    @Test
    void testServicesCacheIsConfigured() {
        assertNotNull(cacheManager);
        assertNotNull(cacheManager.getCache("services"), "services cache should exist");
    }
}