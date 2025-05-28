package cn.mono.aiproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiproxyBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiproxyBootApplication.class, args);
	}

}
