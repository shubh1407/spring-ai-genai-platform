package com.spring_ai.ai_copilot.rag;

import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentReranker {

    List<Document> rerank(List<String> queries,List<Document> documents,int finalTopK);
}
