package com.spring_ai.ai_copilot.rag;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.List;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private DocumentLifeCycleService documentLifeCycleService;

    @InjectMocks
    DocumentIngestionService service;

    @BeforeEach
    public void setup(){
        when(documentLifeCycleService.isFileAlreadyIngested(any(),any(),any())).thenReturn(true);
    }

    @Test
    void testGetDocuments_readsTxtFiles() throws Exception {
        List<Document> docs = service.getDocuments("testdocs","1");
        Assertions.assertFalse(docs.isEmpty(), "Documents should not be empty");
        Assertions.assertTrue(docs.stream().anyMatch(d -> "a.txt".equals(d.getMetadata().get("filename"))));
        Assertions.assertTrue(docs.stream().anyMatch(d -> "b.txt".equals(d.getMetadata().get("filename"))));
        docs.forEach(d -> Assertions.assertEquals("txt", d.getMetadata().get("fileType")));
    }

    @Test
    void testGetDocuments_noExtension_throwsUnsupported() {
        Assertions.assertThrows(com.spring_ai.ai_copilot.exception.UnsupportedDocumentTypeException.class,
            () -> service.getDocuments("testdocs_bad","1"));
    }
}
