package area.server.AREA_Back.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AREA Backend API")
                        .description("API pour la gestion des utilisateurs, services et areas dans l'application AREA")
                        .version("1.0.0"));
    }
}