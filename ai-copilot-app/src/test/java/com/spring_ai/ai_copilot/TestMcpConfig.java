package com.spring_ai.ai_copilot;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestMcpConfig {

    @Bean
    @Primary
    public SyncMcpToolCallbackProvider syncMcpToolCallbackProvider() {
        return SyncMcpToolCallbackProvider.builder().build();
    }
}
