package com.spring_ai.ai_copilot.rag;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class DocumentRegistry {

    private final DocumentRepository documentRepository;

    public DocumentRegistry(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Returns true only if a document row exists with the same tenantId, filename and contentHash.
     * This preserves previous behavior where contentHash was part of the identity check.
     */
    public boolean exists(String tenantId, String fileName, String contentHash) {
        return documentRepository.existsByTenantIdAndFilenameAndContentHash(tenantId, fileName, contentHash);
    }

    /**
     * Registers the document. If a row for (tenantId, filename) already exists, update its content_hash and updated_at.
     * Otherwise insert a new row. This preserves the previous register semantics while enforcing UNIQUE constraint.
     */
    @Transactional
    public void register(String tenantId, String fileName, String contentHash) {
        Optional<DocumentEntity> existing = documentRepository.findByTenantIdAndFilename(tenantId, fileName);
        if (existing.isPresent()) {
            DocumentEntity entity = existing.get();
            entity.setContentHash(contentHash);
            entity.setUpdatedAt(Instant.now());
            documentRepository.save(entity);
            return;
        }

        DocumentEntity entity = new DocumentEntity(UUID.randomUUID(), tenantId, fileName, contentHash);
        try {
            documentRepository.save(entity);
        } catch (DataAccessException ex) {
            // Rare concurrent insert — try to update the existing row instead.
            Optional<DocumentEntity> maybe = documentRepository.findByTenantIdAndFilename(tenantId, fileName);
            maybe.ifPresent(e -> {
                e.setContentHash(contentHash);
                e.setUpdatedAt(Instant.now());
                documentRepository.save(e);
            });
        }
    }
}