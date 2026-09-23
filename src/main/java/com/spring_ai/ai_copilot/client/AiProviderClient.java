package com.spring_ai.ai_copilot.client;

import com.spring_ai.ai_copilot.dto.AgentPlan;
import com.spring_ai.ai_copilot.dto.AgentResponse;
import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.dto.FeedbackAnalysis;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

@Service
public class AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(AiProviderClient.class);
    private static final String PROVIDER = "groq";
    private static final String MODEL = "openai/gpt-oss-120b";

    private final ChatClient client;
    private final StructuredOutputValidationAdvisor validationAdvisor;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
    private final io.github.resilience4j.ratelimiter.RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final AiExceptionTranslator aiExceptionTranslator;

    public AiProviderClient(ChatClient.Builder chatClientBuilder,
                            StructuredOutputValidationAdvisor validationAdvisor,
                            MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                            CircuitBreakerRegistry registry,
                            RateLimiterRegistry rateLimiterRegistry,
                            MeterRegistry meterRegistry,
                            AiExceptionTranslator aiExceptionTranslator) {
        this.client = chatClientBuilder.build();
        this.validationAdvisor = validationAdvisor;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.circuitBreaker = registry.circuitBreaker("aiProvider");
        this.rateLimiter = rateLimiterRegistry.rateLimiter("aiProvider");
        this.meterRegistry = meterRegistry;
        this.aiExceptionTranslator = aiExceptionTranslator;
    }

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public ChatResponse getChatResponse(String systemPrompt,String prompt, String conversationId) {
        long startedAt = System.nanoTime();
        String operation = "chat.response";
        log.info("ai.provider.request.started operation={} provider={} model={} conversationId={}", operation, PROVIDER, MODEL, conversationId);

        try {
            String response = client.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .advisors(messageChatMemoryAdvisor)
                    .advisors(advisorSpec ->
                           advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            recordAiMetrics(operation, "success", null, latencyMs);
            log.info("ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success", operation, PROVIDER, MODEL, latencyMs);
            return new ChatResponse(response);
        } catch (Exception e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String errorCode = "AI_PROVIDER_ERROR";
            recordAiMetrics(operation, "error", errorCode, latencyMs);
            log.warn("ai.provider.request.failed operation={} provider={} model={} latencyMs={} errorCode={} exception={}",
                    operation, PROVIDER, MODEL, latencyMs, errorCode, e.getClass().getSimpleName());
            throw e;
        }
    }


    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public ChatResponse getMultiQuery(String systemPrompt,String prompt) {
        long startedAt = System.nanoTime();
        String operation = "chat.multiquery";
        log.info("ai.provider.request.started operation={} provider={} model={} ", operation, PROVIDER, MODEL);

        try {
            String response = client.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            recordAiMetrics(operation, "success", null, latencyMs);
            log.info("ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success", operation, PROVIDER, MODEL, latencyMs);
            return new ChatResponse(response);
        } catch (Exception e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String errorCode = "AI_PROVIDER_ERROR";
            recordAiMetrics(operation, "error", errorCode, latencyMs);
            log.warn("ai.provider.request.failed operation={} provider={} model={} latencyMs={} errorCode={} exception={}",
                    operation, PROVIDER, MODEL, latencyMs, errorCode, e.getClass().getSimpleName());
            throw e;
        }
    }

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public FeedbackAnalysis getFeedbackAnalysis(String prompt) {
        long startedAt = System.nanoTime();
        String operation = "chat.analysis";
        log.info("ai.provider.request.started operation={} provider={} model={}", operation, PROVIDER, MODEL);

        try {
            FeedbackAnalysis analysis = client.prompt()
                    .system("You are an expert Java and Generative AI assistant.\n" +
                           "Answer technical questions clearly.\n" +
                           "If the user asks about RAG, always interpret it as Retrieval-Augmented Generation.")
                    .user(prompt)
                    .advisors(validationAdvisor)
                    .call().entity(FeedbackAnalysis.class);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            recordAiMetrics(operation, "success", null, latencyMs);
            log.info("ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success", operation, PROVIDER, MODEL, latencyMs);
            return analysis;
        } catch (Exception e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String errorCode = "AI_PROVIDER_ERROR";
            recordAiMetrics(operation, "error", errorCode, latencyMs);
            log.warn("ai.provider.request.failed operation={} provider={} model={} latencyMs={} errorCode={} exception={}",
                    operation, PROVIDER, MODEL, latencyMs, errorCode, e.getClass().getSimpleName());
            throw e;
        }
    }

    public Flux<String> getChatMessageStream(String prompt) {
        String operation = "chat.stream";
        long startedAt = System.nanoTime();
        log.info("ai.provider.request.started operation={} provider={} model={}", operation, PROVIDER, MODEL);

        return client.prompt()
                .system("You are an expert Java and Generative AI assistant.")
                .user(prompt)
                .stream().content()
                .flatMap(text -> Flux.fromArray(text.split(" ")))
                .window(100)
                .flatMap(window -> window.collectList().map(list -> String.join(" ", list)))
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnComplete(() -> {
                    long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                    recordAiMetrics(operation, "success", null, latencyMs);
                    log.info("ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success", operation, PROVIDER, MODEL, latencyMs);
                })
                .doOnError(error -> {
                    long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                    String errorCode = "AI_PROVIDER_ERROR";
                    recordAiMetrics(operation, "error", errorCode, latencyMs);
                    log.warn("ai.provider.request.failed operation={} provider={} model={} latencyMs={} errorCode={} exception={}",
                            operation, PROVIDER, MODEL, latencyMs, errorCode, error.getClass().getSimpleName());
                });
    }

    @CircuitBreaker(name = "aiProvider")
    public Flux<String> getChatMessageStreamingV2(String prompt) {
        String operation = "chat.stream.v2";
        long startedAt = System.nanoTime();
        log.info("ai.provider.request.started operation={} provider={} model={}", operation, PROVIDER, MODEL);

        return client.prompt()
                .system("You are an expert Java and Generative AI assistant.")
                .user(prompt)
                .stream().content()
                .transform(this::generateChunks)
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnComplete(() -> {
                    long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                    recordAiMetrics(operation, "success", null, latencyMs);
                    log.info("ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success", operation, PROVIDER, MODEL, latencyMs);
                })
                .doOnError(error -> {
                    long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                    String errorCode = "AI_PROVIDER_ERROR";
                    recordAiMetrics(operation, "error", errorCode, latencyMs);
                    log.warn("ai.provider.request.failed operation={} provider={} model={} latencyMs={} errorCode={} exception={}",
                            operation, PROVIDER, MODEL, latencyMs, errorCode, error.getClass().getSimpleName());
                });
    }

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name= "aiProvider")
    @CircuitBreaker(name="aiProvider")
    public AgentResponse getChatResponseWithToolCall(String systemPrompt, String prompt, String conversationId, Object ... tools) {

        String operation = "chat.response.tool.call";
        long startedAt = System.nanoTime();
        log.info("ai.provider.request.started operation={} provider={} model={} conversationId={}", operation, PROVIDER, MODEL, conversationId);
        try {
            AgentResponse response = client.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .tools(tools)
                    .advisors(messageChatMemoryAdvisor)
                    .advisors(advisorSpec ->
                            advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(AgentResponse.class);

            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            recordAiMetrics(operation, "success", null, latencyMs);
            log.info("ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success", operation, PROVIDER, MODEL, latencyMs);
            return response;
        } catch (Exception e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String errorCode = "AI_PROVIDER_ERROR";
            recordAiMetrics(operation, "error", errorCode, latencyMs);
            log.warn("ai.provider.request.failed operation={} provider={} model={} latencyMs={} errorCode={} exception={}",
                    operation, PROVIDER, MODEL, latencyMs, errorCode, e.getClass().getSimpleName());
            throw e;
        }


    }

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name= "aiProvider")
    @CircuitBreaker(name="aiProvider")
    public AgentPlan createPlan(String goal) {

        return client.prompt()
                .system("""
                You are an execution planning assistant.

                Create a concise plan for the user's goal.
                Break complex tasks into meaningful steps.
                Do not execute any tools.
                Do not invent information.
                Return only the structured plan.
            """)
                .user(goal)
                .call()
                .entity(AgentPlan.class);
    }

    void recordAiMetrics(String operation, String status, String errorCode, long latencyMs) {
        meterRegistry.counter("ai.requests", "provider", PROVIDER, "model", MODEL, "operation", operation, "status", status).increment();
        if ("error".equals(status)) {
            meterRegistry.counter("ai.errors", "provider", PROVIDER, "model", MODEL, "operation", operation, "status", status, "errorCode", errorCode == null ? "AI_PROVIDER_ERROR" : errorCode).increment();
        }
        meterRegistry.timer("ai.request.duration", "provider", PROVIDER, "model", MODEL, "operation", operation, "status", status).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    private Flux<String> generateChunks(Flux<String> stringFlux) {
        StringBuilder tokenBuffer = new StringBuilder();
        return Flux.create(sink -> {
            stringFlux.subscribe(
                    token -> {
                        tokenBuffer.append(token);
                        if (tokenBuffer.length() >= 100) {
                           sink.next(tokenBuffer.toString());
                           tokenBuffer.setLength(0);
                        }
                    },
                    sink::error,
                    () -> {
                        if (!tokenBuffer.isEmpty()) {
                           sink.next(tokenBuffer.toString());
                        }
                        sink.complete();
                    }
            );
        });
    }
}
