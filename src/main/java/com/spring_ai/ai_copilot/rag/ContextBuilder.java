package com.spring_ai.ai_copilot.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);
    private final MeterRegistry meterRegistry;
    private final AiExceptionTranslator aiExceptionTranslator;

    public ContextBuilder(MeterRegistry meterRegistry, AiExceptionTranslator aiExceptionTranslator) {
        this.meterRegistry = meterRegistry;
        this.aiExceptionTranslator = aiExceptionTranslator;
    }

    public String buildContext(List<Document> documents){
        long startedAt = System.nanoTime();
        log.info("rag.context.started operation=context.documents={}", documents == null ? 0 : documents.size());
        try {
            String context = documents.stream().map(Document::getText)
                    .filter(StringUtils::hasText).collect(Collectors.joining("\n\n"));
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.context.count", "status", "success").increment();
            meterRegistry.timer("rag.context.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
            log.info("rag.context.completed operation=context durationMs={} documentCount={} contextLength={} status=success", durationMs, documents == null ? 0 : documents.size(), context.length());
            return context;
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.context.count", "status", "failure").increment();
            meterRegistry.timer("rag.context.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
            log.warn("rag.context.failed operation=context durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
            throw aiExceptionTranslator.translate(e);
        }
    }
}
