package com.spring_ai.ai_copilot.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ReportGenerateTools {

    @Tool(name = "Report Generate tool",description = "generate report")
    public String generateReport(){
        return "Repot is generated successfully";
    }
}
