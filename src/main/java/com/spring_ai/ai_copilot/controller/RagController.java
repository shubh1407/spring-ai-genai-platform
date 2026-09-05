package com.spring_ai.ai_copilot.controller;

import com.spring_ai.ai_copilot.rag.DocumentChunkingService;
import com.spring_ai.ai_copilot.rag.DocumentEmbeddingService;
import com.spring_ai.ai_copilot.rag.DocumentIngestionService;
import com.spring_ai.ai_copilot.rag.DocumentVectorStoreService;
import kotlin.reflect.jvm.internal.impl.metadata.ProtoBuf;
import kotlin.reflect.jvm.internal.impl.protobuf.MessageLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
public class RagController {

    private final DocumentIngestionService  documentIngestionService;

    private final DocumentChunkingService  documentChunkingService;

    private final DocumentEmbeddingService documentEmbeddingService;

    private final DocumentVectorStoreService documentVectorStoreService;

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    public RagController(DocumentIngestionService documentIngestionService, DocumentChunkingService documentChunkingService, DocumentEmbeddingService documentEmbeddingService, DocumentVectorStoreService documentVectorStoreService) {
        this.documentIngestionService = documentIngestionService;
        this.documentChunkingService = documentChunkingService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.documentVectorStoreService = documentVectorStoreService;
    }

    @GetMapping("rag")
    public List<Document> ragDocuments(@RequestParam String path) throws IOException {
        List<Document> rawDocument = documentIngestionService.getDocuments(path);
        log.info("rawDocument = {}", rawDocument.size());
        List<Document> chunkedDocuments = documentChunkingService.chunkDocuments(rawDocument);
        log.info("chunkedDocuments = {}", chunkedDocuments.size());
        List<float[]> vectors= documentEmbeddingService.getVectorsForDocument(chunkedDocuments);
        log.info("vectors = {}", vectors.size());
        documentVectorStoreService.indexDocuments(chunkedDocuments);
        return chunkedDocuments;
    }

    @GetMapping("vector")
    public float[] getVectors(@RequestParam String text) throws IOException {
        return documentEmbeddingService.getVectorsForText(text);
    }

    @GetMapping("vectorSearch")
    public List<Document> getRelevantChunks(@RequestParam String text) throws IOException {
        List<Document> result= documentVectorStoreService.search(text);
        result.forEach(result1->{
            log.info("result text = {}", result1.getText());
            log.info("result metaData = {}", result1.getMetadata());
        });
        return result;
    }

    @GetMapping("vectorSearchWithTopK")
    public List<Document> getRelevantChunksWithTopK(@RequestParam String text) throws IOException {
        SearchRequest searchRequest = SearchRequest.builder().topK(2).query(text).similarityThreshold(0.7).build();
        List<Document> result= documentVectorStoreService.searchWithRequest(searchRequest);
        result.forEach(result1->{
            log.info("result text = {}", result1.getText());
            log.info("result metaData = {}", result1.getMetadata());
        });
        return result;
    }

    @GetMapping("vectorSearchWithTopKWithFilter")
    public List<Document> getRelevantChunksWithTopKWithFilter(@RequestParam String text,@RequestParam String filter) throws IOException {
        var b = new FilterExpressionBuilder();

        SearchRequest searchRequest = SearchRequest.builder()
                .topK(2)
                .query(text)
                .similarityThreshold(0.7)
                .filterExpression(
                        b.eq("fileType",filter)
                                .build()
                )
                .build();
        List<Document> result= documentVectorStoreService.searchWithRequest(searchRequest);
        result.forEach(result1->{
            log.info("result text = {}", result1.getText());
            log.info("result metaData = {}", result1.getMetadata());
        });
        return result;
    }
}
