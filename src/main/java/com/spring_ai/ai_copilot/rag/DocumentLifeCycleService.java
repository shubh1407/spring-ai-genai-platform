package com.spring_ai.ai_copilot.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

@Service
public class DocumentLifeCycleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentLifeCycleService.class);

    private final DocumentVectorStoreService vectorStore;
    private final DocumentRegistry documentRegistry;
    private final MeterRegistry meterRegistry;

    public DocumentLifeCycleService(DocumentVectorStoreService vectorStore, DocumentRegistry documentRegistry, MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.documentRegistry = documentRegistry;
        this.meterRegistry = meterRegistry;
    }

    public boolean isFileAlreadyIngested(String currentFileHash, String fileName, String tenantId) {
        log.info("rag.ingestion.status.started operation=ingestion file={} tenant={}", fileName, tenantId);
        boolean alreadyIngested = documentRegistry.exists(tenantId,fileName,currentFileHash);
        if (alreadyIngested) {
            meterRegistry.counter("rag.ingestion.skipped", "status", "already_ingested").increment();
            log.info("rag.ingestion.status.completed operation=ingestion file={} tenant={} status=already_ingested", fileName, tenantId);
        }
        return alreadyIngested;
    }

    public void removeOldChunks(String fileName, String tenantId) {
        log.info("rag.ingestion.cleanup.started operation=ingestion file={} tenant={}", fileName, tenantId);
        FilterExpressionBuilder expression = new FilterExpressionBuilder();
        SearchRequest sr = SearchRequest.builder()
                .filterExpression(expression.and(expression.eq("filename", fileName),
                        expression.eq("tenantId", tenantId)).build()).build();
        vectorStore.removeOldChunks(sr);
        log.info("rag.ingestion.cleanup.completed operation=ingestion file={} tenant={} status=success", fileName, tenantId);
        meterRegistry.counter("rag.ingestion.updated", "status", "cleanup").increment();
    }

    public void registerDocuments(String tenantId, String fileName, String contentHash){
        documentRegistry.register(tenantId,fileName,contentHash);
        meterRegistry.counter("rag.ingestion.updated", "status", "registered").increment();
    }
}
