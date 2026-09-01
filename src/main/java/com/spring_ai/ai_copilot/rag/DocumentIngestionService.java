package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import com.spring_ai.ai_copilot.exception.AiProviderException;
import com.spring_ai.ai_copilot.exception.UnsupportedDocumentTypeException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class DocumentIngestionService {

    public List<Document> getDocuments(String path) throws IOException {

        List<Document> documents = new ArrayList<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource [] resources = resolver.getResources("classpath:/"+path+"/*");

        for(Resource resource:resources){
            documents.addAll(getDocumentsFromFile(resource));
        }

        /*documents.forEach(document->{
            System.out.println(document.getMetadata());
        });*/

        return documents;
    }

    private List<Document> getDocumentsFromFile(Resource resource) throws IOException {

        String ext =
                StringUtils.getFilenameExtension(resource.getFilename());

        if (ext == null) {
            throw new UnsupportedDocumentTypeException("file format is not defined");
        }

        List<Document> documents= switch (ext.toLowerCase()) {
            case "txt" -> new TextReader(resource).read();
            case "json" -> new JsonReader(resource).read();
            case "pdf" -> new PagePdfDocumentReader(resource).read();
            default -> throw new UnsupportedDocumentTypeException("file format {"+ext+"} is not supported");
        };

        documents.forEach(document -> {
            document.getMetadata().put("filename", resource.getFilename());
             document.getMetadata().put("fileType",ext);
        });

        return documents;
    }
}
