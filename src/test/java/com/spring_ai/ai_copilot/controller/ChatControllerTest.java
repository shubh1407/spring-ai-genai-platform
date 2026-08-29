package com.spring_ai.ai_copilot.controller;

import com.spring_ai.ai_copilot.dto.ChatRequest;
import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.dto.FeedbackAnalysis;
import com.spring_ai.ai_copilot.service.ChatService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private CircuitBreakerRegistry registry;

    @InjectMocks
    private ChatController chatController;

    @Test
    public void testChatBot(){
        ChatRequest chatRequest = new ChatRequest("1","Hello");
        ChatResponse expectedResponse = new ChatResponse("Hello");
        when(chatService.getChatResponse(chatRequest)).thenReturn(expectedResponse);

        ResponseEntity<ChatResponse> actualResponse = chatController.getChatBot(chatRequest);
        assertNotNull(actualResponse.getBody());
        assertEquals(expectedResponse,actualResponse.getBody());
    }


    @Test
    public void testGetChatMessage(){
        String input = "Product is good";
        FeedbackAnalysis expectedResponse = new FeedbackAnalysis("good",1d,"good response");
        when(chatService.getChatMessage(input)).thenReturn(expectedResponse);

        FeedbackAnalysis actualResponse = chatController.getChatMessage(input);
        assertNotNull(actualResponse);
        assertEquals(expectedResponse,actualResponse);
    }

    @Test
    public void testGetChatMessageStreaming(){
        String input = "Product is good";
        Flux<String> expectedResponse = Flux.just("hello","how can I help you");
        when(chatService.getChatMessageStream(input)).thenReturn(expectedResponse);

        Flux<String> actualResponse = chatController.getChatMessageStreaming(input);
        StepVerifier.create(actualResponse)
                .expectNext("hello")
                .expectNext("how can I help you")
                .verifyComplete();

        verify(chatService).getChatMessageStream(input);
    }

    @Test
    public void testGetChatMessageStreamingV2(){
        String input = "Product is good";
        Flux<String> expectedResponse = Flux.just("hello","how can I help you");
        when(chatService.getChatMessageStreamingV2(input)).thenReturn(expectedResponse);

        Flux<String> actualResponse = chatController.getChatMessageStreamingV2(input);
        StepVerifier.create(actualResponse)
                .expectNext("hello")
                .expectNext("how can I help you")
                .verifyComplete();

        verify(chatService).getChatMessageStreamingV2(input);
    }
}
