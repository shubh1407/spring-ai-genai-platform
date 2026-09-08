package com.spring_ai.ai_copilot.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class QueryTransformer {

    private static final Logger log = LoggerFactory.getLogger(QueryTransformer.class);
    private final MeterRegistry meterRegistry;

    public QueryTransformer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public String transform(String query) {
        long startedAt = System.nanoTime();
        log.info("rag.query.transform.started operation=query.transform inputLength={}", query == null ? 0 : query.length());
        try {
            String transformed = query == null ? "" : query.trim();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.query.transform.count", "status", "success").increment();
            meterRegistry.timer("rag.query.transform.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
            log.info("rag.query.transform.completed operation=query.transform durationMs={} outputLength={} status=success", durationMs, transformed.length());
            return transformed;
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.query.transform.count", "status", "failure").increment();
            meterRegistry.timer("rag.query.transform.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
            log.warn("rag.query.transform.failed operation=query.transform durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
            throw e;
        }
    }
}
