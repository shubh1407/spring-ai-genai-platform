package com.spring_ai.ai_copilot.agent.tools;

import com.spring_ai.ai_copilot.rag.DocumentVectorStoreService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeSearchTool {

    private final DocumentVectorStoreService documentVectorStoreService;

    private final TenantContext tenantContext;

    public KnowledgeSearchTool(DocumentVectorStoreService documentVectorStoreService, TenantContext tenantContext) {
        this.documentVectorStoreService = documentVectorStoreService;
        this.tenantContext = tenantContext;
    }

    @Tool(name = "searchKnowledge",description = "Search the company's internal knowledge base for relevant policies, documentation, and business information.")
    public List<Document> searchKnowledge(@ToolParam(required = true,description = "The user's question or information to search for in the internal knowledge base.") String query){
        SearchRequest sr = SearchRequest.builder()
                .query(query)
                .topK(20)
                .similarityThreshold(0.7)
                .filterExpression(new FilterExpressionBuilder().eq("tenantId", tenantContext.getTenantId()).build())
                .build();
        return documentVectorStoreService.searchWithRequest(sr);
    }

}
