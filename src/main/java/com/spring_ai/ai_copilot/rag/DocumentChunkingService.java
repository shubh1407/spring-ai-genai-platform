package com.spring_ai.ai_copilot.rag;


import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentChunkingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkingService.class);

    private final TextSplitter textSplitter;
    private final MeterRegistry meterRegistry;

    public DocumentChunkingService(TextSplitter textSplitter, MeterRegistry meterRegistry) {
        this.textSplitter = textSplitter;
        this.meterRegistry = meterRegistry;
    }

    public List<Document> chunkDocuments(List<Document> documents) {
        long startedAt = System.nanoTime();
        log.info("rag.chunking.started operation=chunking inputDocuments={}", documents == null ? 0 : documents.size());
        try {
           List<Document> chunkDocuments = textSplitter.apply(documents);
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
           meterRegistry.counter("rag.ingestion.chunks", "status", "success").increment(chunkDocuments.size());
           meterRegistry.timer("rag.ingestion.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
           log.info("rag.chunking.completed operation=chunking inputDocuments={} outputChunks={} durationMs={} status=success", documents == null ? 0 : documents.size(), chunkDocuments.size(), durationMs);
           return chunkDocuments;
        } catch (Exception e) {
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
           meterRegistry.counter("rag.ingestion.chunks", "status", "failure").increment();
           meterRegistry.timer("rag.ingestion.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
           log.warn("rag.chunking.failed operation=chunking durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
           throw e;
        }
    }
}
