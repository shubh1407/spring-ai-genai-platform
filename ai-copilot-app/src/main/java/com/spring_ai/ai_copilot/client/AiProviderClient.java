package com.spring_ai.ai_copilot.client;

import com.spring_ai.ai_copilot.dto.AgentPlan;
import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.dto.FeedbackAnalysis;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

@Service
public class AiProviderClient {

    private static final Logger log =
            LoggerFactory.getLogger(AiProviderClient.class);

    private static final String PROVIDER = "groq";
    private static final String MODEL = "openai/gpt-oss-120b";

    private final ChatClient client;
    private final StructuredOutputValidationAdvisor validationAdvisor;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
    private final io.github.resilience4j.ratelimiter.RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final AiExceptionTranslator aiExceptionTranslator;

    public AiProviderClient(
            ChatClient.Builder chatClientBuilder,
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

    // ============================================================
    // NORMAL CHAT
    // ============================================================

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public ChatResponse getChatResponse(
            String systemPrompt,
            String prompt,
            String conversationId) {

        String operation = "chat.response";
        long startedAt = System.nanoTime();

        log.info(
                "ai.provider.request.started operation={} provider={} model={} conversationId={} promptLength={}",
                operation,
                PROVIDER,
                MODEL,
                conversationId,
                prompt == null ? 0 : prompt.length()
        );

        try {

            String response = client.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .advisors(messageChatMemoryAdvisor)
                    .advisors(advisorSpec ->
                            advisorSpec.param(
                                    ChatMemory.CONVERSATION_ID,
                                    conversationId
                            ))
                    .call()
                    .content();

            long latencyMs = getLatencyMs(startedAt);

            recordAiMetrics(
                    operation,
                    "success",
                    null,
                    latencyMs
            );

            log.info(
                    "ai.provider.request.completed operation={} provider={} model={} conversationId={} latencyMs={} responseLength={} status=success",
                    operation,
                    PROVIDER,
                    MODEL,
                    conversationId,
                    latencyMs,
                    response == null ? 0 : response.length()
            );

            return new ChatResponse(response);

        } catch (Exception e) {

            handleProviderError(
                    operation,
                    conversationId,
                    startedAt,
                    e
            );

            throw e;
        }
    }

    // ============================================================
    // MULTI QUERY
    // ============================================================

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public ChatResponse getMultiQuery(
            String systemPrompt,
            String prompt) {

        String operation = "chat.multiquery";
        long startedAt = System.nanoTime();

        log.info(
                "ai.provider.request.started operation={} provider={} model={} promptLength={}",
                operation,
                PROVIDER,
                MODEL,
                prompt == null ? 0 : prompt.length()
        );

        try {

            String response = client.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();

            long latencyMs = getLatencyMs(startedAt);

            recordAiMetrics(
                    operation,
                    "success",
                    null,
                    latencyMs
            );

            log.info(
                    "ai.provider.request.completed operation={} provider={} model={} latencyMs={} responseLength={} status=success",
                    operation,
                    PROVIDER,
                    MODEL,
                    latencyMs,
                    response == null ? 0 : response.length()
            );

            return new ChatResponse(response);

        } catch (Exception e) {

            handleProviderError(
                    operation,
                    null,
                    startedAt,
                    e
            );

            throw e;
        }
    }

    // ============================================================
    // FEEDBACK ANALYSIS
    // ============================================================

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public FeedbackAnalysis getFeedbackAnalysis(String prompt) {

        String operation = "chat.analysis";
        long startedAt = System.nanoTime();

        log.info(
                "ai.provider.request.started operation={} provider={} model={} promptLength={}",
                operation,
                PROVIDER,
                MODEL,
                prompt == null ? 0 : prompt.length()
        );

        try {

            FeedbackAnalysis analysis = client.prompt()
                    .system("""
                            You are an expert Java and Generative AI assistant.
                            Answer technical questions clearly.
                            If the user asks about RAG, always interpret it as Retrieval-Augmented Generation.
                            """)
                    .user(prompt)
                    .advisors(validationAdvisor)
                    .call()
                    .entity(FeedbackAnalysis.class);

            long latencyMs = getLatencyMs(startedAt);

            recordAiMetrics(
                    operation,
                    "success",
                    null,
                    latencyMs
            );

            log.info(
                    "ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success",
                    operation,
                    PROVIDER,
                    MODEL,
                    latencyMs
            );

            return analysis;

        } catch (Exception e) {

            handleProviderError(
                    operation,
                    null,
                    startedAt,
                    e
            );

            throw e;
        }
    }

    // ============================================================
    // STREAMING
    // ============================================================

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public Flux<String> getChatMessageStream(String prompt) {

        String operation = "chat.stream";
        long startedAt = System.nanoTime();

        log.info(
                "ai.provider.request.started operation={} provider={} model={} promptLength={}",
                operation,
                PROVIDER,
                MODEL,
                prompt == null ? 0 : prompt.length()
        );

        return client.prompt()
                .system("You are an expert Java and Generative AI assistant.")
                .user(prompt)
                .stream()
                .content()

                .flatMap(text ->
                        Flux.fromArray(text.split(" "))
                )

                .window(100)

                .flatMap(window ->
                        window.collectList()
                                .map(list ->
                                        String.join(" ", list))
                )

                .transformDeferred(
                        RateLimiterOperator.of(rateLimiter)
                )

                .transformDeferred(
                        CircuitBreakerOperator.of(circuitBreaker)
                )

                .doOnComplete(() -> {

                    long latencyMs = getLatencyMs(startedAt);

                    recordAiMetrics(
                            operation,
                            "success",
                            null,
                            latencyMs
                    );

                    log.info(
                            "ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success",
                            operation,
                            PROVIDER,
                            MODEL,
                            latencyMs
                    );
                })

                .doOnError(error -> {

                    long latencyMs = getLatencyMs(startedAt);

                    String errorCode = "AI_PROVIDER_ERROR";

                    recordAiMetrics(
                            operation,
                            "error",
                            errorCode,
                            latencyMs
                    );

                    log.warn(
                            "ai.provider.request.failed operation={} provider={} model={} latencyMs={} errorCode={} exception={}",
                            operation,
                            PROVIDER,
                            MODEL,
                            latencyMs,
                            errorCode,
                            error.getClass().getSimpleName()
                    );
                });
    }

    // ============================================================
    // STREAMING V2
    // ============================================================

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public Flux<String> getChatMessageStreamingV2(String prompt) {

        String operation = "chat.stream.v2";
        long startedAt = System.nanoTime();

        log.info(
                "ai.provider.request.started operation={} provider={} model={} promptLength={}",
                operation,
                PROVIDER,
                MODEL,
                prompt == null ? 0 : prompt.length()
        );

        return client.prompt()
                .system("You are an expert Java and Generative AI assistant.")
                .user(prompt)
                .stream()
                .content()

                .transform(this::generateChunks)

                .transformDeferred(
                        RateLimiterOperator.of(rateLimiter)
                )

                .transformDeferred(
                        CircuitBreakerOperator.of(circuitBreaker)
                )

                .doOnComplete(() -> {

                    long latencyMs = getLatencyMs(startedAt);

                    recordAiMetrics(
                            operation,
                            "success",
                            null,
                            latencyMs
                    );

                    log.info(
                            "ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success",
                            operation,
                            PROVIDER,
                            MODEL,
                            latencyMs
                    );
                })

                .doOnError(error -> {

                    long latencyMs = getLatencyMs(startedAt);

                    String errorCode = "AI_PROVIDER_ERROR";

                    recordAiMetrics(
                            operation,
                            "error",
                            errorCode,
                            latencyMs
                    );

                    log.warn(
                            "ai.provider.request.failed operation={} provider={} model={} latencyMs={} errorCode={} exception={}",
                            operation,
                            PROVIDER,
                            MODEL,
                            latencyMs,
                            errorCode,
                            error.getClass().getSimpleName()
                    );
                });
    }

    // ============================================================
    // AGENT / TOOL CALLING
    // ============================================================

    /**
     * Generic structured-output tool calling.
     *
     * IMPORTANT:
     * This generic method is required by the current multi-agent flow.
     * It allows callers to request:
     *
     * SupervisorDecision.class
     * AgentResponse.class
     * any other structured response DTO
     */
    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public <T> T getChatResponseWithToolCall(
            String systemPrompt,
            String prompt,
            String conversationId,
            Class<T> type,
            Object... tools) {

        String operation = "chat.response.tool.call";
        long startedAt = System.nanoTime();

        int toolCount = tools == null ? 0 : tools.length;

        log.info(
                "ai.provider.request.started operation={} provider={} model={} conversationId={} toolCount={} promptLength={}",
                operation,
                PROVIDER,
                MODEL,
                conversationId,
                toolCount,
                prompt == null ? 0 : prompt.length()
        );

        try {

            T response = client.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .tools(tools)
                    .advisors(messageChatMemoryAdvisor)
                    .advisors(advisorSpec ->
                            advisorSpec.param(
                                    ChatMemory.CONVERSATION_ID,
                                    conversationId
                            ))
                    .call()
                    .entity(type);

            long latencyMs = getLatencyMs(startedAt);

            recordAiMetrics(
                    operation,
                    "success",
                    null,
                    latencyMs
            );

            log.info(
                    "ai.provider.request.completed operation={} provider={} model={} conversationId={} toolCount={} latencyMs={} status=success",
                    operation,
                    PROVIDER,
                    MODEL,
                    conversationId,
                    toolCount,
                    latencyMs
            );

            return response;

        } catch (Exception e) {

            handleProviderError(
                    operation,
                    conversationId,
                    startedAt,
                    e
            );

            throw e;
        }
    }

    // ============================================================
    // MCP + TOOL CALLING
    // ============================================================

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public String getChatResponseWithToolCallWithMcpTools(
            String systemPrompt,
            String prompt,
            String conversationId,
            Object... tools) {

        String operation = "chat.response.tool.call.mcp";
        long startedAt = System.nanoTime();

        int toolCount = tools == null ? 0 : tools.length;

        log.info(
                "ai.provider.request.started operation={} provider={} model={} conversationId={} toolCount={} promptLength={}",
                operation,
                PROVIDER,
                MODEL,
                conversationId,
                toolCount,
                prompt == null ? 0 : prompt.length()
        );

        try {

            String response = client.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .tools(tools)
                    .advisors(messageChatMemoryAdvisor)
                    .advisors(advisorSpec ->
                            advisorSpec.param(
                                    ChatMemory.CONVERSATION_ID,
                                    conversationId
                            ))
                    .call()
                    .content();

            long latencyMs = getLatencyMs(startedAt);

            recordAiMetrics(
                    operation,
                    "success",
                    null,
                    latencyMs
            );

            log.info(
                    "ai.provider.request.completed operation={} provider={} model={} conversationId={} toolCount={} latencyMs={} responseLength={} status=success",
                    operation,
                    PROVIDER,
                    MODEL,
                    conversationId,
                    toolCount,
                    latencyMs,
                    response == null ? 0 : response.length()
            );

            return response;

        } catch (Exception e) {

            handleProviderError(
                    operation,
                    conversationId,
                    startedAt,
                    e
            );

            throw e;
        }
    }

    // ============================================================
    // PLANNING
    // ============================================================

    @Bulkhead(name = "aiProvider")
    @RateLimiter(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public AgentPlan createPlan(String goal) {

        String operation = "agent.plan";
        long startedAt = System.nanoTime();

        log.info(
                "ai.provider.request.started operation={} provider={} model={} goalLength={}",
                operation,
                PROVIDER,
                MODEL,
                goal == null ? 0 : goal.length()
        );

        try {

            AgentPlan plan = client.prompt()
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

            long latencyMs = getLatencyMs(startedAt);

            recordAiMetrics(
                    operation,
                    "success",
                    null,
                    latencyMs
            );

            log.info(
                    "ai.provider.request.completed operation={} provider={} model={} latencyMs={} status=success",
                    operation,
                    PROVIDER,
                    MODEL,
                    latencyMs
            );

            return plan;

        } catch (Exception e) {

            handleProviderError(
                    operation,
                    null,
                    startedAt,
                    e
            );

            throw e;
        }
    }

    // ============================================================
    // METRICS
    // ============================================================

    private void recordAiMetrics(
            String operation,
            String status,
            String errorCode,
            long latencyMs) {

        meterRegistry.counter(
                "ai.requests",
                "provider",
                PROVIDER,
                "model",
                MODEL,
                "operation",
                operation,
                "status",
                status
        ).increment();

        if ("error".equals(status)) {

            meterRegistry.counter(
                    "ai.errors",
                    "provider",
                    PROVIDER,
                    "model",
                    MODEL,
                    "operation",
                    operation,
                    "status",
                    status,
                    "errorCode",
                    errorCode == null
                            ? "AI_PROVIDER_ERROR"
                            : errorCode
            ).increment();
        }

        meterRegistry.timer(
                "ai.request.duration",
                "provider",
                PROVIDER,
                "model",
                MODEL,
                "operation",
                operation,
                "status",
                status
        ).record(
                latencyMs,
                TimeUnit.MILLISECONDS
        );
    }

    // ============================================================
    // ERROR HANDLING / LOGGING
    // ============================================================

    private void handleProviderError(
            String operation,
            String conversationId,
            long startedAt,
            Exception e) {

        long latencyMs = getLatencyMs(startedAt);

        String errorCode = "AI_PROVIDER_ERROR";

        recordAiMetrics(
                operation,
                "error",
                errorCode,
                latencyMs
        );

        log.warn(
                "ai.provider.request.failed operation={} provider={} model={} conversationId={} latencyMs={} errorCode={} exception={}",
                operation,
                PROVIDER,
                MODEL,
                conversationId,
                latencyMs,
                errorCode,
                e.getClass().getSimpleName()
        );
    }

    private long getLatencyMs(long startedAt) {

        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }

    // ============================================================
    // STREAM CHUNK GENERATION
    // ============================================================

    private Flux<String> generateChunks(
            Flux<String> stringFlux) {

        StringBuilder tokenBuffer =
                new StringBuilder();

        return Flux.create(sink -> {

            stringFlux.subscribe(

                    token -> {

                        tokenBuffer.append(token);

                        if (tokenBuffer.length() >= 100) {

                            sink.next(
                                    tokenBuffer.toString()
                            );

                            tokenBuffer.setLength(0);
                        }
                    },

                    sink::error,

                    () -> {

                        if (!tokenBuffer.isEmpty()) {

                            sink.next(
                                    tokenBuffer.toString()
                            );
                        }

                        sink.complete();
                    }
            );
        });
    }
}