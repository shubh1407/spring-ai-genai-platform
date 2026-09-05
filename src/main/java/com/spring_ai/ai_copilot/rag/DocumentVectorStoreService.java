package com.spring_ai.ai_copilot.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentVectorStoreService {

    private final VectorStore vectorStore;

    public DocumentVectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void indexDocuments(List<Document> documents){
        vectorStore.add(documents);
    }

    public List<Document> search(String text){
       return vectorStore.similaritySearch(text);
    }

    public List<Document> searchWithRequest(SearchRequest searchRequest){
        return vectorStore.similaritySearch(searchRequest);
    }
}
