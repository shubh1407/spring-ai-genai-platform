package com.spring_ai.ai_copilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestMcpConfig.class)
class AiCopilotApplicationTests {

	@Test
	void contextLoads() {
		System.out.println("AiCopilotApplicationTests");
	}

}
