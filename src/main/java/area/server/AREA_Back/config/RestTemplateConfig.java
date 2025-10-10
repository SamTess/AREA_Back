package area.server.AREA_Back.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate bean
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a RestTemplate bean with proper configuration
     * for HTTP client timeouts and connection management
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(10000);
                    factory.setReadTimeout(30000);
                    return factory;
                })
                .build();
    }
}