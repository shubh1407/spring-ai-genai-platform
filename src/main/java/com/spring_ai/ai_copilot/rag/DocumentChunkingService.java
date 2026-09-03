package com.spring_ai.ai_copilot.rag;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import org.springframework.ai.document.Document;
import java.util.List;

@Service
public class DocumentChunkingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkingService.class);

    private final TextSplitter textSplitter;

    public DocumentChunkingService(TextSplitter textSplitter) {
        this.textSplitter = textSplitter;
    }

    public List<Document> chunkDocuments(List<Document> documents) {
        log.trace("chunkDocuments({})", documents.size());
        List<Document> chunkDocuments = textSplitter.apply(documents);
        log.info("Chunked documents: " + chunkDocuments.size());
        return  chunkDocuments;
    }

    /*public static void main(String[] args) {
        Document document = new Document("""
        Company Refund Policy

        Customers can request a refund within 30 days of purchase.

        Enterprise customers can request a refund within 60 days
        if their contract allows it.

        Refund requests must include the original order ID.

        Refunds are normally processed within 5 business days.
        """);

        new DocumentChunkingService(null).chunkDocuments(List.of(document));
    }*/
}
