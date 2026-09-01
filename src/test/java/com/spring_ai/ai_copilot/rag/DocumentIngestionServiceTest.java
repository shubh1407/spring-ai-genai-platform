package com.spring_ai.ai_copilot.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.List;
import org.springframework.ai.document.Document;

class DocumentIngestionServiceTest {

    DocumentIngestionService service = new DocumentIngestionService();

    @Test
    void testGetDocuments_readsTxtFiles() throws Exception {
        List<Document> docs = service.getDocuments("testdocs");
        Assertions.assertFalse(docs.isEmpty(), "Documents should not be empty");
        Assertions.assertTrue(docs.stream().anyMatch(d -> "a.txt".equals(d.getMetadata().get("filename"))));
        Assertions.assertTrue(docs.stream().anyMatch(d -> "b.txt".equals(d.getMetadata().get("filename"))));
        docs.forEach(d -> Assertions.assertEquals("txt", d.getMetadata().get("fileType")));
    }

    @Test
    void testGetDocuments_noExtension_throwsUnsupported() {
        Assertions.assertThrows(com.spring_ai.ai_copilot.exception.UnsupportedDocumentTypeException.class,
            () -> service.getDocuments("testdocs_bad"));
    }
}
