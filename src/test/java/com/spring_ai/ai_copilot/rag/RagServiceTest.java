package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.dto.Citation;
import com.spring_ai.ai_copilot.dto.RagRequest;
import com.spring_ai.ai_copilot.dto.RagResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    DocumentIngestionService ingestionService;
    @Mock
    DocumentChunkingService chunkingService;
    @Mock
    DocumentEmbeddingService embeddingService;
    @Mock
    DocumentVectorStoreService vectorStoreService;
    @Mock
    AiProviderClient aiProviderClient;
    @Mock
    ContextBuilder ctxBuilder;
    @Mock
    CitationBuilder citationBuilder;
    @Mock
    QueryTransformer queryTransformer;
    @Mock
    DocumentReranker documentReranker;
    @Mock
    MultiQueryGenerator multiQueryGenerator;
    @Mock
    MultiQueryRetriever multiQueryRetriever;
    @Mock
    io.micrometer.core.instrument.MeterRegistry meterRegistry;
    @Mock
    com.spring_ai.ai_copilot.exception.AiExceptionTranslator aiExceptionTranslator;

    RagService ragService;

    @org.junit.jupiter.api.BeforeEach
    void init() {
        ragService = new RagService(ingestionService, chunkingService, embeddingService, vectorStoreService, aiProviderClient, ctxBuilder, citationBuilder, queryTransformer, documentReranker, multiQueryGenerator, multiQueryRetriever, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), aiExceptionTranslator);
    }

    @Test
    void ask_returns_rag_response_with_citations() {
        RagRequest req = new RagRequest("conv1","what is refund?","tenantX");

        when(queryTransformer.transform(req.question())).thenReturn("refund?");
        when(multiQueryGenerator.generateMultiQuery("refund?")).thenReturn(List.of("refund?"));

        Document d = mock(Document.class);
        when(multiQueryRetriever.searchDocuments(anyList(), eq("tenantX"))).thenReturn(List.of(d));
        when(documentReranker.rerank(anyList(), anyList(), eq(5))).thenReturn(List.of(d));
        when(ctxBuilder.buildContext(anyList())).thenReturn("context text");
        when(aiProviderClient.getChatResponse(anyString(), anyString(), anyString())).thenReturn(new ChatResponse("answer text"));
        when(citationBuilder.extractSources(anyList())).thenReturn(List.of(new Citation("company-policy.txt", "company-policy.txt", "txt", "", "")));

        RagResponse resp = ragService.ask(req);

        assertNotNull(resp);
        assertTrue(resp.message().contains("answer"));
        assertNotNull(resp.sources());
        assertEquals(1, resp.sources().size());
        assertEquals("company-policy.txt", resp.sources().getFirst().source());
    }
}
