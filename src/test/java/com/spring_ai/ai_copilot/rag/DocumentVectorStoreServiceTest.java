package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DocumentVectorStoreServiceTest {

    @Mock
    VectorStore vectorStore;

    @Mock
    AiExceptionTranslator aiExceptionTranslator;

    DocumentVectorStoreService service;

    @org.junit.jupiter.api.BeforeEach
    void init() {
        service = new DocumentVectorStoreService(vectorStore, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), aiExceptionTranslator);
    }

    @Test
    void indexDocuments_calls_vectorstore_add() {
        Document d = mock(Document.class);
        service.indexDocuments(List.of(d));
        verify(vectorStore).add(any());
    }

    @Test
    void indexDocuments_translates_exceptions() {
        doThrow(new RuntimeException("boom")).when(vectorStore).add(any());
        AiProviderException translated = new AiProviderException(HttpStatus.GATEWAY_TIMEOUT,
                "AI_PROVIDER_TIMEOUT",
                "AI provider request timed out",
                new RuntimeException());
        when(aiExceptionTranslator.translate(any())).thenReturn(translated);

        AiProviderException ex = assertThrows(AiProviderException.class, () -> service.indexDocuments(List.of(mock(Document.class))));
        assertSame(translated, ex);
    }

}
