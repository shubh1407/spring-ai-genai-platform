package com.spring_ai.ai_copilot.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentVectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(DocumentVectorStoreService.class);

    private final VectorStore vectorStore;
    private final MeterRegistry meterRegistry;

    public DocumentVectorStoreService(VectorStore vectorStore, MeterRegistry meterRegistry) {
       this.vectorStore = vectorStore;
       this.meterRegistry = meterRegistry;
    }

    public void indexDocuments(List<Document> documents){
       long startedAt = System.nanoTime();
       log.info("rag.vector.index.started operation=index.documents count={}", documents == null ? 0 : documents.size());
       try {
           if(!CollectionUtils.isEmpty(documents)) {
               vectorStore.add(documents);
           }
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
           meterRegistry.counter("rag.vector.index.count", "status", "success").increment(documents == null ? 0 : documents.size());
           meterRegistry.timer("rag.vector.index.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
           log.info("rag.vector.index.completed operation=index.documents count={} durationMs={} status=success", documents == null ? 0 : documents.size(), durationMs);
       } catch (Exception e) {
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
           meterRegistry.counter("rag.vector.index.count", "status", "failure").increment();
           meterRegistry.timer("rag.vector.index.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
           log.warn("rag.vector.index.failed operation=index.documents durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
           throw e;
       }
    }

    public List<Document> search(String text){
       long startedAt = System.nanoTime();
       log.info("rag.retrieval.started operation=vector.search queryLength={}", text == null ? 0 : text.length());
       try {
           List<Document> results = vectorStore.similaritySearch(text);
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
           meterRegistry.counter("rag.retrieval.count", "status", "success").increment();
           meterRegistry.timer("rag.retrieval.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
           if (results == null || results.isEmpty()) {
               meterRegistry.counter("rag.retrieval.empty", "status", "empty").increment();
           }
           log.info("rag.retrieval.completed operation=vector.search resultCount={} durationMs={} status=success", results == null ? 0 : results.size(), durationMs);
           return results;
       } catch (Exception e) {
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
           meterRegistry.counter("rag.retrieval.count", "status", "failure").increment();
           meterRegistry.timer("rag.retrieval.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
           log.warn("rag.retrieval.failed operation=vector.search durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
           throw e;
       }
    }

    public List<Document> searchWithRequest(SearchRequest searchRequest){
       long startedAt = System.nanoTime();
       log.info("rag.retrieval.started operation=vector.search.topk query={} topK={}", searchRequest.getQuery(), searchRequest.getTopK());
       try {
           List<Document> results = vectorStore.similaritySearch(searchRequest);
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
           meterRegistry.counter("rag.retrieval.count", "status", "success").increment();
           meterRegistry.timer("rag.retrieval.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
           if (results == null || results.isEmpty()) {
               meterRegistry.counter("rag.retrieval.empty", "status", "empty").increment();
           }
           log.info("rag.retrieval.completed operation=vector.search.topk resultCount={} durationMs={} status=success", results == null ? 0 : results.size(), durationMs);
           return results;
       } catch (Exception e) {
           long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
           meterRegistry.counter("rag.retrieval.count", "status", "failure").increment();
           meterRegistry.timer("rag.retrieval.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
           log.warn("rag.retrieval.failed operation=vector.search.topk durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
           throw e;
       }
    }

    public void removeOldChunks(SearchRequest searchRequest){
       if (searchRequest.getFilterExpression() != null) {
           vectorStore.delete(searchRequest.getFilterExpression());
       }
    }
}
