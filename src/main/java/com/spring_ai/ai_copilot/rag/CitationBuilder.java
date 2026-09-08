package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.dto.Citation;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class CitationBuilder {

    private static final Logger log = LoggerFactory.getLogger(CitationBuilder.class);
    private final MeterRegistry meterRegistry;

    public CitationBuilder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public List<Citation> extractSources(List<Document> documents) {
        long startedAt = System.nanoTime();
        log.info("rag.citations.started operation=citations documents={}", documents == null ? 0 : documents.size());
        try {
            List<Citation> citations = documents.stream().map(this::getSource).distinct().toList();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.citations.count", "status", "success").increment();
            meterRegistry.timer("rag.citations.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
            log.info("rag.citations.completed operation=citations durationMs={} count={} status=success", durationMs, citations.size());
            return citations;
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.citations.count", "status", "failure").increment();
            meterRegistry.timer("rag.citations.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
            log.warn("rag.citations.failed operation=citations durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
            throw e;
        }
    }

    private Citation getSource(Document document) {
        return switch (document.getMetadata().getOrDefault("fileType","").toString()){
            case "txt", "json" -> new Citation(document.getMetadata().getOrDefault("source",document.getMetadata().getOrDefault("filename", "")).toString(),document.getMetadata().getOrDefault("filename","").toString(),document.getMetadata().getOrDefault("fileType","").toString(),"",document.getMetadata().getOrDefault("chunk_index","").toString());
            case "pdf" -> new Citation(document.getMetadata().getOrDefault("source",document.getMetadata().getOrDefault("filename", "")).toString(),document.getMetadata().getOrDefault("filename","").toString(),document.getMetadata().getOrDefault("fileType","").toString(),document.getMetadata().getOrDefault("page_number","").toString(),document.getMetadata().getOrDefault("chunk_index","").toString());
            default ->
                    throw new IllegalStateException("Unexpected value: " + document.getMetadata().getOrDefault("fileType","").toString());
        };
    }
}
