package com.spring_ai.ai_copilot.rag;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DocumentRegistryTest {

    @Mock
    private DocumentRepository repository;

    @InjectMocks
    private DocumentRegistry registry;

    @Test
    void exists_delegates_to_repository() {
        when(repository.existsByTenantIdAndFilenameAndContentHash("t","f","h")).thenReturn(true);
        assertTrue(registry.exists("t","f","h"));
        verify(repository).existsByTenantIdAndFilenameAndContentHash("t","f","h");
    }

    @Test
    void register_inserts_when_missing() {
        when(repository.findByTenantIdAndFilename("t","f")).thenReturn(Optional.empty());
        DocumentEntity saved = new DocumentEntity(UUID.randomUUID(), "t","f","h");
        when(repository.save(any(DocumentEntity.class))).thenReturn(saved);

        registry.register("t","f","h");

        verify(repository).save(any(DocumentEntity.class));
    }

    @Test
    void register_updates_when_present() {
        DocumentEntity existing = new DocumentEntity(UUID.randomUUID(), "t","f","oldhash");
        when(repository.findByTenantIdAndFilename("t","f")).thenReturn(Optional.of(existing));

        registry.register("t","f","newhash");

        assertEquals("newhash", existing.getContentHash());
        verify(repository).save(existing);
    }
}
