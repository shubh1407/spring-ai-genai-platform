package com.spring_ai.ai_copilot.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagRetrievalService {

    private final QueryTransformer queryTransformer;
    private final DocumentReranker documentReranker;
    private final MultiQueryGenerator multiQueryGenerator;
    private final MultiQueryRetriever multiQueryRetriever;

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    public RagRetrievalService(QueryTransformer queryTransformer, DocumentReranker documentReranker, MultiQueryGenerator multiQueryGenerator, MultiQueryRetriever multiQueryRetriever) {
        this.queryTransformer = queryTransformer;
        this.documentReranker = documentReranker;
        this.multiQueryGenerator = multiQueryGenerator;
        this.multiQueryRetriever = multiQueryRetriever;
    }


    public List<Document> retrieveDocument(String question, String tenantId) {
        String transformedQuery = queryTransformer.transform(question);
        log.info("rag.ask.query.transform.completed operation=ask tenant={} questionLength={} status=success", tenantId, transformedQuery.length());

        List<String> queries = multiQueryGenerator.generateMultiQuery(transformedQuery);
        log.info("rag.ask.multi.query.completed operation=ask tenant={} queryCount={} status=success", tenantId, queries.size());

        List<Document> candidates = multiQueryRetriever.searchDocuments(queries, tenantId);
        log.info("rag.ask.retrieval.completed operation=ask tenant={} candidateCount={} status=success", tenantId, candidates.size());

        return documentReranker.rerank(queries, candidates, 5);
    }
}
