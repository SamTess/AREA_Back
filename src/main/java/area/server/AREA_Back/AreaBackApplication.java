package area.server.AREA_Back;

import area.server.AREA_Back.config.RedisStreamProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RedisStreamProperties.class)
public class AreaBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(AreaBackApplication.class, args);
	}

}
