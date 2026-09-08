package com.spring_ai.ai_copilot.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class MultiQueryRetriever {

    private final DocumentVectorStoreService vectorStoreService;
    private final MeterRegistry meterRegistry;

    private final static Logger log = LoggerFactory.getLogger(MultiQueryRetriever.class);

    public MultiQueryRetriever(DocumentVectorStoreService vectorStoreService, MeterRegistry meterRegistry) {
        this.vectorStoreService = vectorStoreService;
        this.meterRegistry = meterRegistry;
    }

    public List<Document> searchDocuments(List<String> queries, String tenantId) {
        long startedAt = System.nanoTime();
        log.info("rag.multi.retrieval.started operation=multi.retrieval tenant={} queries={}", tenantId, queries == null ? 0 : queries.size());

        try {
            List<Document> results = queries.stream()
                    .flatMap(query -> {
                        SearchRequest searchRequest = SearchRequest.builder()
                                .query(query)
                                .filterExpression(new FilterExpressionBuilder().eq("tenantId", tenantId).build())
                                .topK(10)
                                .build();

                        List<Document> queryResults = vectorStoreService.searchWithRequest(searchRequest);
                        log.info("Query [{}] returned {} documents for tenant {}", query, queryResults.size(), tenantId);
                        return queryResults.stream();
                    })
                    .distinct()
                    .toList();

            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.retrieval.count", "status", "success").increment();
            meterRegistry.timer("rag.retrieval.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
            if (results.isEmpty()) {
                meterRegistry.counter("rag.retrieval.empty", "status", "empty").increment();
            }
            log.info("rag.multi.retrieval.completed operation=multi.retrieval tenant={} uniqueDocuments={} durationMs={} status=success", tenantId, results.size(), durationMs);
            return results;
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.retrieval.count", "status", "failure").increment();
            meterRegistry.timer("rag.retrieval.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
            log.warn("rag.multi.retrieval.failed operation=multi.retrieval tenant={} durationMs={} exception={}", tenantId, durationMs, e.getClass().getSimpleName(), e);
            throw e;
        }
    }
}
