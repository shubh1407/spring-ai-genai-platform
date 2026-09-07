package com.spring_ai.ai_copilot.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
public class DocumentLifeCycleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentLifeCycleService.class);

    private final DocumentVectorStoreService vectorStore;
    private final DocumentRegistry documentRegistry;

    public DocumentLifeCycleService(DocumentVectorStoreService vectorStore, DocumentRegistry documentRegistry) {
        this.vectorStore = vectorStore;
        this.documentRegistry = documentRegistry;
    }

    public boolean isFileAlreadyIngested(String currentFileHash, String fileName, String tenantId) {
        log.info("Checking ingestion status for file {} in tenant {} with hash {}", fileName, tenantId, currentFileHash);
        boolean alreadyIngested = documentRegistry.exists(tenantId,fileName,currentFileHash);
        if (alreadyIngested) {
            log.info("File {} for tenant {} is already ingested; found {} matching chunks", fileName, tenantId,alreadyIngested);
        }
        return alreadyIngested;
    }

    public void removeOldChunks(String fileName, String tenantId) {
        log.info("Removing previously indexed chunks for file {} in tenant {}", fileName, tenantId);
        FilterExpressionBuilder expression = new FilterExpressionBuilder();
        SearchRequest sr = SearchRequest.builder()
                .filterExpression(expression.and(expression.eq("filename", fileName),
                        expression.eq("tenantId", tenantId)).build()).build();
        vectorStore.removeOldChunks(sr);
        log.info("Completed chunk cleanup for file {} in tenant {}", fileName, tenantId);
    }

    public void registerDocuments(String tenantId, String fileName, String contentHash){
        documentRegistry.register(tenantId,fileName,contentHash);
    }
}
