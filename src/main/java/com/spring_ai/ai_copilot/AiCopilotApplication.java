package com.spring_ai.ai_copilot;

import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication(exclude = { OpenAiEmbeddingAutoConfiguration.class })
public class AiCopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiCopilotApplication.class, args);
	}

}
