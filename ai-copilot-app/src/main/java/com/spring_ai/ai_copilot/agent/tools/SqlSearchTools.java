package com.spring_ai.ai_copilot.agent.tools;

import com.spring_ai.ai_copilot.dto.PurchaseDetailsDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SqlSearchTools {

    @Tool(name = "PurchaseDetails", description = """
            Retrieves purchase details for a customer purchase, including the purchase date, order ID, and customer type (STANDARD or ENTERPRISE).
            
            Use this tool when the delegated task requires retrieving purchase-related information needed for business decisions such as refund eligibility.
            
            The tool returns only data available for the requested purchase. Do not infer or fabricate missing values.
            """)
    public PurchaseDetailsDto getPurchaseDetails(@ToolParam(description = "Customer Id",required = false) String customerId){
        return new PurchaseDetailsDto("20-Aug-2026","1","Standard");
    }
}
