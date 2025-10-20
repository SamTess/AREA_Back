package area.server.AREA_Back.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
* Configuration for RestTemplate bean with Apache HttpClient for PATCH support
*/
@Configuration
public class RestTemplateConfig {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;

    /**
     * Creates a CloseableHttpClient bean with proper configuration
     */
    @Bean
    public CloseableHttpClient httpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setResponseTimeout(Timeout.ofMilliseconds(READ_TIMEOUT_MS))
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    /**
     * Creates a RestTemplate bean with HttpComponentsClientHttpRequestFactory
     * to support PATCH and other HTTP methods
     */
    @Bean
    public RestTemplate restTemplate(CloseableHttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }
}