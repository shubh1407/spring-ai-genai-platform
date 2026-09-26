package com.spring_ai.ai_copilot.controller;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ChatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void shouldReturnChatResponse() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("hello from ai")))));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hello from ai"));
    }

    @Test
    void shouldReturnFeedbackAnalysis() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"sentiment\":\"positive\",\"score\":1.0,\"summary\":\"good response\"}")))));

        mockMvc.perform(get("/api/chat")
                        .param("message", "good"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentiment").value("positive"))
                .andExpect(jsonPath("$.score").value(1.0))
                .andExpect(jsonPath("$.summary").value("good response"));
    }

    @Test
    void shouldStreamChatResponse() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage("hello")))),
                        new ChatResponse(List.of(new Generation(new AssistantMessage(" world"))))
                ));

        mockMvc.perform(get("/api/chat/stream")
                        .param("message", "hello"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andExpect(content().string(containsString("hello")))
                .andExpect(content().string(containsString("world")));
    }

    @Test
    void shouldStreamChatResponseV2() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage("hello world"))))
                ));

        mockMvc.perform(get("/api/chat/streamV2")
                        .param("message", "hello"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(MediaType.TEXT_EVENT_STREAM_VALUE)));
    }

    @Test
    void shouldReturnBadRequestOnValidationFailure() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnProviderError() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("provider down"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_PROVIDER_ERROR"));
    }

    @Test
    void shouldReturnTimeoutError() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException(new TimeoutException("timed out")));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\"hello\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("AI_PROVIDER_TIMEOUT"));
    }

    @Test
    void shouldReturnRateLimitError() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("demo")));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\"hello\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("AI_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void shouldReturnBulkheadError() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("demo")));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI_PROVIDER_BUSY"));
    }

    @Test
    void shouldReturnCircuitBreakerError() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("demo")));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI_PROVIDER_UNAVAILABLE"));
    }
}
