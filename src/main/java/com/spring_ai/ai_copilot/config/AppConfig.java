package com.spring_ai.ai_copilot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring_ai.ai_copilot.dto.FeedbackAnalysis;
import com.spring_ai.ai_copilot.interceptor.GroqReasoningInterceptor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public StructuredOutputValidationAdvisor getStructuredOutputValidationAdvisor() {
        return StructuredOutputValidationAdvisor.builder()
                .outputType(FeedbackAnalysis.class)
                .maxRepeatAttempts(3)
                .build();
    }

    @Bean
    public MessageWindowChatMemory  getMessageWindowChatMemory() {
        return MessageWindowChatMemory.builder().build();
    }

    @Bean
    public MessageChatMemoryAdvisor getMessageChatMemoryAdvisor(MessageWindowChatMemory messageWindowChatMemory) {
        return MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build();
    }

    @Bean
    public ObjectMapper getObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public OpenAiHttpClientBuilderCustomizer groqReasoningInterceptor(
            ObjectMapper objectMapper) {

        return builder -> builder.interceptor(
                new GroqReasoningInterceptor(objectMapper)
        );
    }
}
