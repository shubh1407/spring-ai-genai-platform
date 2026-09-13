package com.spring_ai.ai_copilot.agent.tools;

import com.spring_ai.ai_copilot.dto.Citation;
import com.spring_ai.ai_copilot.dto.ToolResponse;
import com.spring_ai.ai_copilot.exception.ToolValidationException;
import com.spring_ai.ai_copilot.rag.CitationBuilder;
import com.spring_ai.ai_copilot.rag.RagRetrievalService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeSearchTool {

    private final RagRetrievalService ragRetrievalService;

    private final CitationBuilder citationBuilder;

    private final TenantContext tenantContext;

    public KnowledgeSearchTool(RagRetrievalService ragRetrievalService, CitationBuilder citationBuilder, TenantContext tenantContext) {
        this.ragRetrievalService = ragRetrievalService;
        this.citationBuilder = citationBuilder;
        this.tenantContext = tenantContext;
    }

    @Tool(name = "searchKnowledge",description = "Search the company's internal knowledge base for relevant policies, documentation, and business information.")
    public ToolResponse searchKnowledge(@ToolParam(required = true,description = "The user's question or information to search for in the internal knowledge base.") String query){

        if (query == null || query.isBlank()) {
            throw new ToolValidationException("Search query must not be blank");
        }

        if (query.length() > 500) {
            throw new ToolValidationException("Search query is too long");
        }

        List<Document> finalDocuments = ragRetrievalService.retrieveDocument(query,tenantContext.getTenantId());
        List<Citation> citations = citationBuilder.extractSources(finalDocuments);
        return new ToolResponse(finalDocuments,citations);
    }

}
