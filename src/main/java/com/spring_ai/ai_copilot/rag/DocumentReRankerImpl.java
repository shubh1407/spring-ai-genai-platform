package com.spring_ai.ai_copilot.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DocumentReRankerImpl implements DocumentReranker {

    private static final Logger log = LoggerFactory.getLogger(DocumentReRankerImpl.class);
    private final MeterRegistry meterRegistry;

    public DocumentReRankerImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public List<Document> rerank(
            List<String> queries,
            List<Document> documents,
            int finalTopK) {
        long startedAt = System.nanoTime();
        log.info("rag.reranking.started operation=reranking candidates={} topK={}", documents == null ? 0 : documents.size(), finalTopK);
        try {
            List<Document> reranked = documents.stream()
                    .sorted(
                            Comparator.comparingInt(document -> score((Document) document, queries)).reversed()
                    )
                    .limit(finalTopK)
                    .toList();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.reranking.count", "status", "success").increment();
            meterRegistry.timer("rag.reranking.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
            log.info("rag.reranking.completed operation=reranking candidates={} final={} durationMs={} status=success", documents.size(), reranked.size(), durationMs);
            return reranked;
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.reranking.count", "status", "failure").increment();
            meterRegistry.timer("rag.reranking.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
            log.warn("rag.reranking.failed operation=reranking durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
            throw e;
        }
    }

    private int score(Document document, List<String> queries) {

        String text = document.getText();

        if (!StringUtils.hasText(text)
                || queries == null
                || queries.isEmpty()) {
            return 0;
        }

        String normalizedText = text.toLowerCase();

        return queries.stream()
                .mapToInt(query -> {
                    if (!StringUtils.hasText(query)) {
                        return 0;
                    }

                    String[] queryWords =
                            query.toLowerCase().split("\\s+");

                    return (int) Arrays.stream(queryWords)
                            .filter(normalizedText::contains)
                            .count();
                })
                .max()
                .orElse(0);
    }
}