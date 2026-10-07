package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.dto.Citation;
import com.spring_ai.ai_copilot.dto.RagRequest;
import com.spring_ai.ai_copilot.dto.RagResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.spring_ai.ai_copilot.constant.PromptsConstants.RAG_SYSTEM_PROMPT;

@Service
public class RagService {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentChunkingService documentChunkingService;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final DocumentVectorStoreService documentVectorStoreService;
    private final AiProviderClient aiProviderClient;
    private final ContextBuilder ctxBuilder;
    private final CitationBuilder citationBuilder;
    private final MeterRegistry meterRegistry;
    private final AiExceptionTranslator aiExceptionTranslator;
    private final RagRetrievalService ragRetrievalService;

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    public RagService(DocumentIngestionService documentIngestionService, DocumentChunkingService documentChunkingService, DocumentEmbeddingService documentEmbeddingService, DocumentVectorStoreService documentVectorStoreService, AiProviderClient aiProviderClient, ContextBuilder ctxBuilder, CitationBuilder citationBuilder, MeterRegistry meterRegistry, AiExceptionTranslator aiExceptionTranslator, RagRetrievalService ragRetrievalService) {
        this.documentIngestionService = documentIngestionService;
        this.documentChunkingService = documentChunkingService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.documentVectorStoreService = documentVectorStoreService;
        this.aiProviderClient = aiProviderClient;
        this.ctxBuilder = ctxBuilder;
        this.citationBuilder = citationBuilder;
        this.meterRegistry = meterRegistry;
        this.aiExceptionTranslator = aiExceptionTranslator;
        this.ragRetrievalService = ragRetrievalService;
    }


    public List<Document> ragDocuments(String path, String tenantId) throws IOException {
        log.info("rag.ingestion.request.started operation=ingestion path={} tenant={}", path, tenantId);
        List<Document> documents = documentIngestionService.getDocuments(path, tenantId);
        log.info("rag.ingestion.request.documentsFound operation=ingestion tenant={} rawDocuments={}", tenantId, documents.size());
        List<Document> chunkedDocuments = documentChunkingService.chunkDocuments(documents);
        log.info("rag.ingestion.request.chunked operation=ingestion tenant={} inputDocuments={} outputChunks={}", tenantId, documents.size(), chunkedDocuments.size());
        documentVectorStoreService.indexDocuments(chunkedDocuments);
        documents.forEach(document -> {
            if (document != null) {
               Map<String, Object> metaData = document.getMetadata();
               documentIngestionService.registerDocuments(tenantId, metaData.get("filename").toString(), metaData.get("contentHash").toString());
            }
        });
        log.info("rag.ingestion.request.completed operation=ingestion tenant={} indexedChunks={} status=success", tenantId, chunkedDocuments.size());
        return chunkedDocuments;
    }

    public float[] getVectors(String text) throws IOException {
        log.info("rag.embedding.started operation=embedding inputLength={}", text.length());
        return documentEmbeddingService.getVectorsForText(text);
    }

    public List<Document> searchDocuments(String query) {
        log.info("rag.vector.search.started operation=vector.search query={}", query);
        return documentVectorStoreService.search(query);
    }

    public List<Document> searchDocuments(SearchRequest searchRequest) {
        log.info("rag.vector.search.started operation=vector.search.structured query={} topK={}", searchRequest.getQuery(), searchRequest.getTopK());
        return documentVectorStoreService.searchWithRequest(searchRequest);
    }

    public RagResponse ask(RagRequest ragRequest) {
        long startedAt = System.nanoTime();
        String tenantId = ragRequest.tenantId();
        log.info("rag.ask.started operation=ask tenant={} conversationId={}", tenantId, ragRequest.conversationId());
        try {
            List<Document> finalDocuments=ragRetrievalService.retrieveDocument(ragRequest.question(), tenantId);
            log.info("rag.ask.reranking.completed operation=ask tenant={} finalDocuments={} status=success", tenantId, finalDocuments.size());

            String context = ctxBuilder.buildContext(finalDocuments);
            log.info("rag.ask.context.completed operation=ask tenant={} contextLength={} status=success", ragRequest.tenantId(), context.length());

            PromptTemplate promptTemplate = PromptTemplate.builder().template("""
                You are a helpful AI assistant.
                  
                Answer the user's question using the provided context.
                  
                Context:
                {context}
                  
                Question:
                {question}""").build();

            String prompt = promptTemplate.render(Map.of("question", ragRequest.question(), "context", context));
            ChatResponse chatResponse = aiProviderClient.getChatResponse(RAG_SYSTEM_PROMPT, prompt, ragRequest.conversationId());
            log.info("rag.ask.llm.completed operation=ask tenant={} conversationId={} status=success", ragRequest.tenantId(), ragRequest.conversationId());

            List<Citation> citations = citationBuilder.extractSources(finalDocuments);
            log.info("rag.ask.citations.completed operation=ask tenant={} citations={} status=success", ragRequest.tenantId(), citations.size());

            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.ask.count", "status", "success").increment();
            meterRegistry.timer("rag.ask.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
            log.info("rag.ask.completed operation=ask tenant={} conversationId={} durationMs={} status=success", ragRequest.tenantId(), ragRequest.conversationId(), durationMs);
            return new RagResponse(chatResponse.message(), citations);
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.ask.count", "status", "failure").increment();
            meterRegistry.counter("rag.ask.failure", "status", "failure").increment();
            meterRegistry.timer("rag.ask.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
            log.error("rag.ask.failed operation=ask tenant={} conversationId={} durationMs={} exception={}", ragRequest.tenantId(), ragRequest.conversationId(), durationMs, e.getClass().getSimpleName(), e);
            throw aiExceptionTranslator.translate(e);
        }
    }

    public Map<String, Object> evaluate(String tenantId, String question, String expectedSources, int topK) {
        long startedAt = System.nanoTime();
        log.info("rag.evaluation.started operation=evaluation tenant={} topK={} expectedSources={}", tenantId, topK, expectedSources);
        try {
            List<Document> finalDocuments=ragRetrievalService.retrieveDocument(question, tenantId);
            log.info("rag.ask.reranking.completed operation=ask tenant={} finalDocuments={} status=success", tenantId, finalDocuments.size());

            Set<String> expectedSet = new HashSet<>();
            if (expectedSources != null && !expectedSources.isBlank()) {
               Arrays.stream(expectedSources.split("[,;|]"))
                       .map(String::trim)
                       .filter(value -> !value.isEmpty())
                       .forEach(expectedSet::add);
            }

            int relevant = 0;
            for (int i = 0; i < finalDocuments.size() && i < topK; i++) {
               Document doc = finalDocuments.get(i);
               String filename = doc.getMetadata().getOrDefault("filename", "").toString();
               if (expectedSet.contains(filename)) {
                   relevant++;
               }
            }

            double precisionAtK = expectedSet.isEmpty() ? 0.0 : relevant / (double) Math.max(1, Math.min(topK, finalDocuments.size()));
            double recallAtK = expectedSet.isEmpty() ? 0.0 : relevant / (double) expectedSet.size();
            double mrr = 0.0;
            int rank = 1;
            for (Document doc : finalDocuments) {
               String filename = doc.getMetadata().getOrDefault("filename", "").toString();
               if (expectedSet.contains(filename)) {
                   mrr = 1.0 / rank;
                   break;
               }
               rank++;
            }

            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.evaluation.count", "status", "success").increment();
            meterRegistry.timer("rag.evaluation.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
            meterRegistry.summary("rag.evaluation.precision", "status", "success").record(precisionAtK);
            meterRegistry.summary("rag.evaluation.recall", "status", "success").record(recallAtK);
            meterRegistry.summary("rag.evaluation.mrr", "status", "success").record(mrr);
            log.info("rag.evaluation.completed operation=evaluation tenant={} precision={} recall={} mrr={} durationMs={} status=success", tenantId, precisionAtK, recallAtK, mrr, durationMs);
            return Map.of(
                   "tenantId", tenantId,
                   "precisionAtK", precisionAtK,
                   "recallAtK", recallAtK,
                   "mrr", mrr,
                   "retrievedDocuments", finalDocuments.size(),
                   "expectedSources", expectedSet
            );
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.evaluation.count", "status", "failure").increment();
            meterRegistry.timer("rag.evaluation.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
            log.error("rag.evaluation.failed operation=evaluation tenant={} durationMs={} exception={}", tenantId, durationMs, e.getClass().getSimpleName(), e);
            throw aiExceptionTranslator.translate(e);
        }
    }


}
