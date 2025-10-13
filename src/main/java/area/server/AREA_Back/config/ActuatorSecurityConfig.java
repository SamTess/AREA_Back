package area.server.AREA_Back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration for Actuator endpoints security.
 */
@Configuration
public class ActuatorSecurityConfig {

    /**
     * Security configuration for actuator endpoints.
     * This allows prometheus endpoint to be accessed without authentication
     * while keeping other actuator endpoints secured.
     *
     * @param http the HttpSecurity to configure
     * @return the SecurityFilterChain
     * @throws Exception if an error occurs
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/**")
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> httpBasic.realmName("Actuator"));
        return http.build();
    }
}