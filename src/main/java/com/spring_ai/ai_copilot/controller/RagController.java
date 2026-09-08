package com.spring_ai.ai_copilot.controller;

import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.dto.RagRequest;
import com.spring_ai.ai_copilot.dto.RagResponse;
import com.spring_ai.ai_copilot.rag.*;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("rag")
    public List<Document> ragDocuments(@RequestParam String path, @RequestParam String tenantId) throws IOException {
        return ragService.ragDocuments(path,tenantId);
    }

    @GetMapping("vector")
    public float[] getVectors(@RequestParam String text) throws IOException {
        return ragService.getVectors(text);
    }

    @GetMapping("vectorSearch")
    public List<Document> getRelevantChunks(@RequestParam String text) throws IOException {
        List<Document> result= ragService.searchDocuments(text);
        result.forEach(result1->{
            log.info("result text = {}", result1.getText());
            log.info("result metaData = {}", result1.getMetadata());
        });
        return result;
    }

    @GetMapping("vectorSearchWithTopK")
    public List<Document> getRelevantChunksWithTopK(@RequestParam String text) throws IOException {
        SearchRequest searchRequest = SearchRequest.builder().topK(5).query(text).similarityThreshold(0.7).build();
        List<Document> result= ragService.searchDocuments(searchRequest);
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
                .topK(5)
                .query(text)
                .similarityThreshold(0.7)
                .filterExpression(
                        b.eq("fileType",filter)
                                .build()
                )
                .build();
        List<Document> result= ragService.searchDocuments(searchRequest);
        result.forEach(result1->{
            log.info("result text = {}", result1.getText());
            log.info("result metaData = {}", result1.getMetadata());
        });
        return result;
    }

    @GetMapping("ask")
    public RagResponse askQuestion(@Valid @RequestBody RagRequest ragRequest){
        return ragService.ask(ragRequest);
    }

    @GetMapping("evaluation")
    public Map<String, Object> evaluate(
            @RequestParam String tenantId,
            @RequestParam String question,
            @RequestParam(required = false) String expectedSources,
            @RequestParam(defaultValue = "5") int topK) {
        return ragService.evaluate(tenantId, question, expectedSources, topK);
    }
}
