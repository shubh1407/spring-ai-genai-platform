package com.spring_ai.ai_copilot.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public VectorStore vectorStore(@Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel){
        return SimpleVectorStore.builder(embeddingModel).build();
    }

}
