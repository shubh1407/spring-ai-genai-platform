package com.spring_ai.ai_copilot.controller;

import com.spring_ai.ai_copilot.rag.DocumentChunkingService;
import com.spring_ai.ai_copilot.rag.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
public class RagController {

    private final DocumentIngestionService  documentIngestionService;

    private final DocumentChunkingService  documentChunkingService;

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    public RagController(DocumentIngestionService documentIngestionService, DocumentChunkingService documentChunkingService) {
        this.documentIngestionService = documentIngestionService;
        this.documentChunkingService = documentChunkingService;
    }

    @GetMapping("rag")
    public List<Document> ragDocuments(@RequestParam String path) throws IOException {
        List<Document> rawDocument = documentIngestionService.getDocuments(path);
        log.info("rawDocument = {}", rawDocument.size());
        List<Document> chunkedDocuments = documentChunkingService.chunkDocuments(rawDocument);
        log.info("chunkedDocuments = {}", chunkedDocuments.size());
        return  chunkedDocuments;
    }
}
