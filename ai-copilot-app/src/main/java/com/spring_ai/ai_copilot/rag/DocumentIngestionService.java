package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.exception.UnsupportedDocumentTypeException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentLifeCycleService documentLifeCycleService;
    private final MeterRegistry meterRegistry;

    public DocumentIngestionService(DocumentLifeCycleService documentLifeCycleService, MeterRegistry meterRegistry) {
        this.documentLifeCycleService = documentLifeCycleService;
        this.meterRegistry = meterRegistry;
    }

    public List<Document> getDocuments(String path, String tenantId) throws IOException {
        long startedAt = System.nanoTime();
        log.info("rag.ingestion.started operation=ingestion path={} tenant={}", path, tenantId);

        List<Document> documents = new ArrayList<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/" + path + "/*");
        log.info("rag.ingestion.discovered operation=ingestion path={} tenant={} files={}", path, tenantId, resources.length);

        int skippedFiles = 0;
        int updatedFiles = 0;
        List<Document> retrievedDocs;
        for (Resource resource : resources) {
            retrievedDocs = getDocumentsFromFile(resource, tenantId);
            if (retrievedDocs != null) {
                documents.addAll(retrievedDocs);
                updatedFiles++;
            } else {
                skippedFiles++;
            }
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        meterRegistry.counter("rag.ingestion.count", "status", "success").increment();
        meterRegistry.timer("rag.ingestion.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
        meterRegistry.counter("rag.ingestion.skipped", "status", "success").increment(skippedFiles);
        meterRegistry.counter("rag.ingestion.updated", "status", "success").increment(updatedFiles);
        log.info("rag.ingestion.completed operation=ingestion tenant={} discovered={} updated={} skipped={} documents={} durationMs={} status=success", tenantId, resources.length, updatedFiles, skippedFiles, documents.size(), durationMs);
        return documents;
    }

    private List<Document> getDocumentsFromFile(Resource resource, String tenantId) throws IOException {

        String ext = StringUtils.getFilenameExtension(resource.getFilename());

        if (ext == null) {
            log.error("rag.ingestion.failed operation=ingestion resource={} tenant={} errorCode=RAG_VALIDATION_ERROR exception=unsupported.document.type", resource.getFilename(), tenantId);
            throw new UnsupportedDocumentTypeException("file format is not defined");
        }

        log.info("rag.ingestion.file.started operation=ingestion resource={} extension={} tenant={}", resource.getFilename(), ext, tenantId);

        String contentHash = DigestUtils.md5DigestAsHex(
                resource.getInputStream().readAllBytes()
        );

        List<Document> documents = switch (ext.toLowerCase()) {
            case "txt" -> new TextReader(resource).read();
            case "json" -> new JsonReader(resource).read();
            case "pdf" -> new PagePdfDocumentReader(resource).read();
            default -> throw new UnsupportedDocumentTypeException("file format {" + ext + "} is not supported");
        };

        boolean isFileExist = documentLifeCycleService.isFileAlreadyIngested(contentHash, resource.getFilename(), tenantId);
        if (isFileExist) {
            meterRegistry.counter("rag.ingestion.skipped", "status", "duplicate").increment();
            log.info("rag.ingestion.file.skipped operation=ingestion resource={} tenant={} reason=already.ingested", resource.getFilename(), tenantId);
            return null;
        }

        documentLifeCycleService.removeOldChunks(resource.getFilename(), tenantId);

        documents.forEach(document -> {
            document.getMetadata().put("filename", resource.getFilename());
            document.getMetadata().put("fileType", ext);
            document.getMetadata().put("contentHash", contentHash);
            document.getMetadata().put("tenantId", tenantId);
        });

        log.info("rag.ingestion.file.completed operation=ingestion resource={} tenant={} documents={} status=success", resource.getFilename(), tenantId, documents.size());
        return documents;
    }

    public void registerDocuments(String tenantId, String fileName, String contentHash){
        log.info("rag.ingestion.registered operation=ingestion tenant={} file={} status=success", tenantId, fileName);
        documentLifeCycleService.registerDocuments(tenantId, fileName, contentHash);
    }
}
