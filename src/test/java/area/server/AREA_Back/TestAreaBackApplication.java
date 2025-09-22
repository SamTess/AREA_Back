package area.server.AREA_Back;

import org.springframework.boot.SpringApplication;

public class TestAreaBackApplication {

	public static void main(String[] args) {
		SpringApplication.from(AreaBackApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
