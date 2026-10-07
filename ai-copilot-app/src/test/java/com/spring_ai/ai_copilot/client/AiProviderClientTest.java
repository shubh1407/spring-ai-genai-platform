package com.spring_ai.ai_copilot.client;

import com.spring_ai.ai_copilot.agent.tools.KnowledgeSearchTool;
import com.spring_ai.ai_copilot.dto.ChatRequest;
import com.spring_ai.ai_copilot.dto.FeedbackAnalysis;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static com.spring_ai.ai_copilot.constant.PromptsConstants.CHAT_SYSTEM_PROMPT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiProviderClientTest {

    @Test
    void shouldRecordAiMetricsForSuccessfulAndFailedRequests() {
        MeterRegistry registry = new SimpleMeterRegistry();
        ChatModel fakeChatModel = prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage("ok")))
        );

        AiProviderClient client = new AiProviderClient(
                ChatClient.builder(fakeChatModel),
                StructuredOutputValidationAdvisor.builder()
                        .outputType(FeedbackAnalysis.class)
                        .maxRepeatAttempts(3)
                        .build(),
                MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build(),
                CircuitBreakerRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults(),
                registry,
                new AiExceptionTranslator()
        );

        client.recordAiMetrics("chat.response", "success", null, 250L);
        client.recordAiMetrics("chat.response", "error", "AI_PROVIDER_TIMEOUT", 125L);

        assertEquals(1.0d, registry.counter("ai.requests", "provider", "groq", "model", "openai/gpt-oss-120b", "operation", "chat.response", "status", "success").count(), 0.0d);
        assertEquals(1.0d, registry.counter("ai.errors", "provider", "groq", "model", "openai/gpt-oss-120b", "operation", "chat.response", "status", "error", "errorCode", "AI_PROVIDER_TIMEOUT").count(), 0.0d);
        assertEquals(1.0d, registry.find("ai.request.duration").tag("provider", "groq").tag("model", "openai/gpt-oss-120b").tag("operation", "chat.response").tag("status", "success").timer().count(), 0.0d);
    }

    @Test
    void shouldReturnChatResponse() {
        ChatModel fakeChatModel = prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage("hello from ai")))
        );

        AiProviderClient client = createClient(fakeChatModel);

        var response = client.getChatResponse(CHAT_SYSTEM_PROMPT,"hello", new ChatRequest("c1", "hello").conversationId());

        assertNotNull(response);
        assertEquals("hello from ai", response.message());
    }

    @Test
    void shouldReturnFeedbackAnalysis() {
        ChatModel fakeChatModel = prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage("{\"sentiment\":\"good\",\"score\":1.0,\"summary\":\"nice response\"}")))
        );

        AiProviderClient client = createClient(fakeChatModel);

        FeedbackAnalysis analysis = client.getFeedbackAnalysis("analyze");

        assertNotNull(analysis);
        assertEquals("good", analysis.sentiment());
        assertEquals(1.0, analysis.score());
        assertEquals("nice response", analysis.summary());
    }

    @Test
    void shouldThrowWhenProviderFails() {
        ChatModel fakeChatModel = prompt -> {
            throw new RuntimeException("provider down");
        };

        AiProviderClient client = createClient(fakeChatModel);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> client.getChatResponse(CHAT_SYSTEM_PROMPT,"hello", new ChatRequest("c1", "hello").conversationId()));

        assertEquals("provider down", exception.getMessage());
    }

    @Test
    void shouldStreamChatMessage() {
        ChatModel fakeChatModel = mock(ChatModel.class);
        when(fakeChatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(fakeChatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("hello")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("world"))))
        ));

        AiProviderClient client = createClient(fakeChatModel);

        StepVerifier.create(client.getChatMessageStream("hello"))
                .expectNext("hello world")
                .verifyComplete();
    }

    @Test
    void shouldStreamChatMessageV2() {
        ChatModel fakeChatModel = mock(ChatModel.class);
        when(fakeChatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(fakeChatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("hello"))))
        ));

        AiProviderClient client = createClient(fakeChatModel);

        StepVerifier.create(client.getChatMessageStreamingV2("hello"))
                .expectNext("hello")
                .verifyComplete();
    }

    private AiProviderClient createClient(ChatModel fakeChatModel) {
        return new AiProviderClient(
                ChatClient.builder(fakeChatModel),
                StructuredOutputValidationAdvisor.builder()
                        .outputType(FeedbackAnalysis.class)
                        .maxRepeatAttempts(3)
                        .build(),
                MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build(),
                CircuitBreakerRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults(),
                new SimpleMeterRegistry(),
                new AiExceptionTranslator()
        );
    }
}
