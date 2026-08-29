package com.spring_ai.ai_copilot.controller;

import com.spring_ai.ai_copilot.dto.ChatRequest;
import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.dto.FeedbackAnalysis;
import com.spring_ai.ai_copilot.service.ChatService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final CircuitBreakerRegistry registry;

    public ChatController(ChatService chatService, CircuitBreakerRegistry registry) {
        this.chatService = chatService;
        this.registry = registry;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> getChatBot(@Valid @RequestBody ChatRequest chatRequest) {
        log.info("controller.chat.request.started operation=chat.request");
        try {
            ChatResponse response = chatService.getChatResponse(chatRequest);
            log.info("controller.chat.request.completed operation=chat.request status=success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("controller.chat.request.failed operation=chat.request status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    /*@GetMapping("/debug/circuit")
    public String circuitState() {
        log.info("controller.circuit.state.started operation=circuit.state");
        try {
            CircuitBreaker cb = registry.circuitBreaker("aiProvider");
            String state = "state=" + cb.getState()
                    + ", failureRate=" + cb.getMetrics().getFailureRate()
                    + ", bufferedCalls=" + cb.getMetrics().getNumberOfBufferedCalls();
            log.info("controller.circuit.state.completed operation=circuit.state status=success");
            return state;
        } catch (Exception e) {
            log.warn("controller.circuit.state.failed operation=circuit.state status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }*/

    @GetMapping
    public FeedbackAnalysis getChatMessage(@RequestParam String message) {
        log.info("controller.chat.analysis.started operation=chat.analysis");
        try {
            FeedbackAnalysis analysis = chatService.getChatMessage(message);
            log.info("controller.chat.analysis.completed operation=chat.analysis status=success");
            return analysis;
        } catch (Exception e) {
            log.warn("controller.chat.analysis.failed operation=chat.analysis status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getChatMessageStreaming(@RequestParam String message) {
        log.info("controller.chat.stream.started operation=chat.stream");
        try {
            Flux<String> stream = chatService.getChatMessageStream(message);
            log.info("controller.chat.stream.completed operation=chat.stream status=success");
            return stream;
        } catch (Exception e) {
            log.warn("controller.chat.stream.failed operation=chat.stream status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @GetMapping(value = "/streamV2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getChatMessageStreamingV2(@RequestParam String message) {
        log.info("controller.chat.streamV2.started operation=chat.stream.v2");
        try {
            Flux<String> stream = chatService.getChatMessageStreamingV2(message);
            log.info("controller.chat.streamV2.completed operation=chat.stream.v2 status=success");
            return stream;
        } catch (Exception e) {
            log.warn("controller.chat.streamV2.failed operation=chat.stream.v2 status=error exception={}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
