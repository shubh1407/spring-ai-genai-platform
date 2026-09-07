package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import com.spring_ai.ai_copilot.exception.AiProviderException;
import com.spring_ai.ai_copilot.exception.UnsupportedDocumentTypeException;
import com.spring_ai.ai_copilot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentLifeCycleService documentLifeCycleService;

    public DocumentIngestionService(DocumentLifeCycleService documentLifeCycleService) {
        this.documentLifeCycleService = documentLifeCycleService;
    }

    public List<Document> getDocuments(String path, String tenantId) throws IOException {

        log.info("Getting documents from {} for tenant {}", path, tenantId);
        List<Document> documents = new ArrayList<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/" + path + "/*");
        log.info("Discovered {} files for path {} in tenant {}", resources.length, path, tenantId);

        List<Document> retrievedDocs;
        for (Resource resource : resources) {
            retrievedDocs = getDocumentsFromFile(resource, tenantId);
            if (retrievedDocs != null) {
                documents.addAll(retrievedDocs);
            }
        }

        log.info("Found {} documents for tenant {} after ingestion scan", documents.size(), tenantId);
        return documents;
    }

    private List<Document> getDocumentsFromFile(Resource resource, String tenantId) throws IOException {

        String ext = StringUtils.getFilenameExtension(resource.getFilename());

        if (ext == null) {
            log.error("Extension is invalid for resource {} and tenant {}", resource.getFilename(), tenantId);
            throw new UnsupportedDocumentTypeException("file format is not defined");
        }

        log.info("Processing file {} with extension {} for tenant {}", resource.getFilename(), ext, tenantId);

        String contentHash = DigestUtils.md5DigestAsHex(
                resource.getInputStream().readAllBytes()
        );
        log.info("Computed content hash for {} for tenant {}", resource.getFilename(), tenantId);

        List<Document> documents = switch (ext.toLowerCase()) {
            case "txt" -> new TextReader(resource).read();
            case "json" -> new JsonReader(resource).read();
            case "pdf" -> new PagePdfDocumentReader(resource).read();
            default -> throw new UnsupportedDocumentTypeException("file format {" + ext + "} is not supported");
        };

        boolean isFileExist = documentLifeCycleService.isFileAlreadyIngested(contentHash, resource.getFilename(), tenantId);
        if (isFileExist) {
            log.info("Skipping already ingested file {} for tenant {}", resource.getFilename(), tenantId);
            return null;
        }

        documentLifeCycleService.removeOldChunks(resource.getFilename(), tenantId);

        documents.forEach(document -> {
            document.getMetadata().put("filename", resource.getFilename());
            document.getMetadata().put("fileType", ext);
            document.getMetadata().put("contentHash", contentHash);
            document.getMetadata().put("tenantId", tenantId);
        });

        log.info("Prepared {} documents from {} for tenant {}", documents.size(), resource.getFilename(), tenantId);
        return documents;
    }

    public void registerDocuments(String tenantId, String fileName, String contentHash){
        documentLifeCycleService.registerDocuments(tenantId, fileName, contentHash);
    }
}
