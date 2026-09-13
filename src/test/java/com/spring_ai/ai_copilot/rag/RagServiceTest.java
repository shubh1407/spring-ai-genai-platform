package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.dto.ChatResponse;
import com.spring_ai.ai_copilot.dto.Citation;
import com.spring_ai.ai_copilot.dto.RagRequest;
import com.spring_ai.ai_copilot.dto.RagResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import io.micrometer.core.instrument.MeterRegistry;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

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
    MeterRegistry meterRegistry;
    @Mock
    AiExceptionTranslator aiExceptionTranslator;

    @Mock
    private Counter counter;

    @Mock
    private Timer timer;

    @Mock
    RagRetrievalService ragRetrievalService;

    @InjectMocks
    RagService ragService;

    @Test
    void ask_returns_rag_response_with_citations() {
        RagRequest req = new RagRequest("conv1","what is refund?","tenantX");

        Document d = mock(Document.class);
        when(ragRetrievalService.retrieveDocument(req.question(),req.tenantId())).thenReturn(List.of(d));
        when(ctxBuilder.buildContext(anyList())).thenReturn("context text");
        when(aiProviderClient.getChatResponse(anyString(), anyString(), anyString())).thenReturn(new ChatResponse("answer text"));
        when(citationBuilder.extractSources(anyList())).thenReturn(List.of(new Citation("company-policy.txt", "company-policy.txt", "txt", "", "")));

        when(meterRegistry.counter(anyString(), any(String[].class)))
                .thenReturn(counter);

        when(meterRegistry.timer(anyString(), any(String[].class)))
                .thenReturn(timer);

        RagResponse resp = ragService.ask(req);

        assertNotNull(resp);
        assertTrue(resp.message().contains("answer"));
        assertNotNull(resp.sources());
        assertEquals(1, resp.sources().size());
        assertEquals("company-policy.txt", resp.sources().getFirst().source());
    }
}
