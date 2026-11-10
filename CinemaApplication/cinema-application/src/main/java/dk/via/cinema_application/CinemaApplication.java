
package dk.via.cinema_application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
		scanBasePackages = {
				"dk.via.cinema",
				"dk.via.cinema_application"
		}
)
@EntityScan(basePackages = "dk.via.cinema.entity")
@EnableJpaRepositories(
		basePackages = "dk.via.cinema.repository"
)
public class CinemaApplication {

	public static void main(String[] args) {
		SpringApplication.run(CinemaApplication.class, args);
	}
}
