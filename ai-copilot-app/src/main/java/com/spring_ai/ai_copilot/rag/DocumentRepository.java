package com.spring_ai.ai_copilot.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByTenantIdAndFilename(String tenantId, String filename);

    boolean existsByTenantIdAndFilenameAndContentHash(String tenantId, String filename, String contentHash);
}
