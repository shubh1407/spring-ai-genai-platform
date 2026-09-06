package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.dto.Citation;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CitationBuilder {

    public List<Citation> extractSources(List<Document> documents) {
        return documents.stream().map(this::getSource).distinct().toList();
    }

    private Citation getSource(Document document) {
        return switch (document.getMetadata().getOrDefault("fileType","").toString()){
            case "txt", "json" -> new Citation(document.getMetadata().getOrDefault("source",document.getMetadata().getOrDefault("filename", "")).toString(),document.getMetadata().getOrDefault("filename","").toString(),document.getMetadata().getOrDefault("fileType","").toString(),"",document.getMetadata().getOrDefault("chunk_index","").toString());
            case "pdf" -> new Citation(document.getMetadata().getOrDefault("source",document.getMetadata().getOrDefault("filename", "")).toString(),document.getMetadata().getOrDefault("filename","").toString(),document.getMetadata().getOrDefault("fileType","").toString(),document.getMetadata().getOrDefault("page_number","").toString(),document.getMetadata().getOrDefault("chunk_index","").toString());
            default ->
                    throw new IllegalStateException("Unexpected value: " + document.getMetadata().getOrDefault("fileType","").toString());
        };
    }
}
