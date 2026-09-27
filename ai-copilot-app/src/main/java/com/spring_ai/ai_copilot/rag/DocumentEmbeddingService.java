package com.spring_ai.ai_copilot.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public DocumentEmbeddingService(@Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }


    public float[] getVectorsForText(String text){
        return embeddingModel.embed(text);
    }

    public List<float[]> getVectorsForDocument(List<Document> documents){
        return documents.stream().map(Document::getText)
                .map(this::getVectorsForText)
                .toList();
    }

}
