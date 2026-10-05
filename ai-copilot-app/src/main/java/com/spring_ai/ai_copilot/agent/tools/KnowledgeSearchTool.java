package com.spring_ai.ai_copilot.agent.tools;

import com.spring_ai.ai_copilot.dto.Citation;
import com.spring_ai.ai_copilot.dto.RefundEligibilityResponse;
import com.spring_ai.ai_copilot.dto.ToolResponse;
import com.spring_ai.ai_copilot.exception.ToolValidationException;
import com.spring_ai.ai_copilot.rag.CitationBuilder;
import com.spring_ai.ai_copilot.rag.RagRetrievalService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class KnowledgeSearchTool {

    private final RagRetrievalService ragRetrievalService;

    private final CitationBuilder citationBuilder;

    public KnowledgeSearchTool(RagRetrievalService ragRetrievalService, CitationBuilder citationBuilder) {
        this.ragRetrievalService = ragRetrievalService;
        this.citationBuilder = citationBuilder;
    }

    @Tool(name = "searchKnowledge", description = "Search the company's internal knowledge base for relevant policies, documentation, and business information.")
    public ToolResponse searchKnowledge(@ToolParam(required = true, description = "The user's question or information to search for in the internal knowledge base.") String query,
                                        @ToolParam(description = "Unique tenant ID used to scope the knowledge search to the correct tenant.") String tenantId) {

        if (query == null || query.isBlank()) {
            throw new ToolValidationException("Search query must not be blank");
        }

        if (query.length() > 500) {
            throw new ToolValidationException("Search query is too long");
        }

        List<Document> finalDocuments = ragRetrievalService.retrieveDocument(query, tenantId);
        List<Citation> citations = citationBuilder.extractSources(finalDocuments);
        return new ToolResponse(finalDocuments, citations);
    }

    @Tool(name = "checkRefundEligibility", description = "Check whether a customer is eligible for a refund based on the applicable refund window and purchase date.")
    public RefundEligibilityResponse checkRefundEligibility(@ToolParam(required = true,
                                                       description = "The name of the refund policy to apply."
                                               )
                                               String policyName,
                                                            @ToolParam(
                                                       required = true,
                                                       description = "The customer type, such as STANDARD or ENTERPRISE."
                                               )
                                               String customerType,

                                                            @ToolParam(
                                                       required = true,
                                                       description = "The purchase date in yyyy-MM-dd format."
                                               )
                                               String purchaseDate
    ) {
        if (policyName == null || policyName.isBlank()) {
            throw new ToolValidationException("Policy name must not be blank");
        }

        if (customerType == null || customerType.isBlank()) {
            throw new ToolValidationException("Customer type must not be blank");
        }

        if (purchaseDate == null || purchaseDate.isBlank()) {
            throw new ToolValidationException("Purchase date must not be blank");
        }

        LocalDate purchase = LocalDate.parse(purchaseDate);
        LocalDate currentDate = LocalDate.now();

        int refundWindowDays = switch (customerType.toUpperCase()) {
            case "STANDARD" -> 45;
            case "ENTERPRISE" -> 60;
            default -> throw new ToolValidationException(
                    "Unsupported customer type: " + customerType
            );
        };

        long daysSincePurchase = ChronoUnit.DAYS.between(purchase, currentDate);

        boolean eligible =
                daysSincePurchase >= 0 &&
                        daysSincePurchase <= refundWindowDays;

        String reason = eligible
                ? "Purchase is within the " + refundWindowDays + "-day refund window."
                : "Purchase is outside the " + refundWindowDays + "-day refund window.";

        return new RefundEligibilityResponse(
                eligible,
                customerType.toUpperCase(),
                purchaseDate,
                refundWindowDays,
                daysSincePurchase,
                reason
        );
    }
}
