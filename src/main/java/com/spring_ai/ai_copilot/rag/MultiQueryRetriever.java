package com.spring_ai.ai_copilot.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MultiQueryRetriever {

    private final DocumentVectorStoreService vectorStoreService;

    private final static Logger log = LoggerFactory.getLogger(MultiQueryRetriever.class);

    public MultiQueryRetriever(DocumentVectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    public List<Document> searchDocuments(List<String> queries) {

        return queries.stream()
                .flatMap(query -> {
                    SearchRequest searchRequest = SearchRequest.builder()
                            .query(query)
                            .topK(10)
                            .build();

                    List<Document> results =
                            vectorStoreService.searchWithRequest(searchRequest);

                    log.info("Query [{}] returned {} documents", query, results.size());

                    results.forEach(document ->
                            log.info("Query [{}] -> {}", query, document.getText()));

                    return results.stream();
                })
                .distinct()
                .toList();
    }
}
