package com.spring_ai.ai_copilot.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MultiQueryRetriever {

    private final DocumentVectorStoreService vectorStoreService;

    private final static Logger log = LoggerFactory.getLogger(MultiQueryRetriever.class);

    public MultiQueryRetriever(DocumentVectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    public List<Document> searchDocuments(List<String> queries, String tenantId) {
        log.info("Starting multi-query retrieval for tenant {} with {} queries", tenantId, queries.size());

        List<Document> results = queries.stream()
                .flatMap(query -> {
                    SearchRequest searchRequest = SearchRequest.builder()
                            .query(query)
                            .filterExpression(new FilterExpressionBuilder().eq("tenantId", tenantId).build())
                            .topK(10)
                            .build();

                    List<Document> queryResults = vectorStoreService.searchWithRequest(searchRequest);
                    log.info("Query [{}] returned {} documents for tenant {}", query, queryResults.size(), tenantId);

                    queryResults.forEach(document ->
                            log.info("Query [{}] -> {}", query, document.getText()));

                    return queryResults.stream();
                })
                .distinct()
                .toList();

        log.info("Completed multi-query retrieval for tenant {} with {} unique documents", tenantId, results.size());
        return results;
    }
}
