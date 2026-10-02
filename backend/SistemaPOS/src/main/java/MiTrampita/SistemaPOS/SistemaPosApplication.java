package MiTrampita.SistemaPOS;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class SistemaPosApplication {

	public static void main(String[] args) {
		SpringApplication.run(SistemaPosApplication.class, args);
	}

}
