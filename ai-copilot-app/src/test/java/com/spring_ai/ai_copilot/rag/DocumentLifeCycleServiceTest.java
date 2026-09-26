package com.spring_ai.ai_copilot.rag;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentLifeCycleServiceTest {

    @Mock
    DocumentVectorStoreService vectorStore;

    @Mock
    DocumentRegistry registry;

    DocumentLifeCycleService lifecycle;

    @org.junit.jupiter.api.BeforeEach
    void init() {
        lifecycle = new DocumentLifeCycleService(vectorStore, registry, new SimpleMeterRegistry());
    }

    @Test
    void isFileAlreadyIngested_delegates() {
        when(registry.exists("t","f","h")).thenReturn(true);
        boolean r = lifecycle.isFileAlreadyIngested("h","f","t");
        org.junit.jupiter.api.Assertions.assertTrue(r);
        verify(registry).exists("t","f","h");
    }

    @Test
    void removeOldChunks_calls_vectorstore() {
        lifecycle.removeOldChunks("file.txt","tenantX");
        verify(vectorStore).removeOldChunks(any());
    }

    @Test
    void registerDocuments_calls_registry() {
        lifecycle.registerDocuments("t","file","h");
        verify(registry).register("t","file","h");
    }
}
