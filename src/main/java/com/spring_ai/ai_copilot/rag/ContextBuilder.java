package com.spring_ai.ai_copilot.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ContextBuilder {

    public String buildContext(List<Document> documents){
        return documents.stream().map(Document::getText)
                .filter(StringUtils::hasText).collect(Collectors.joining("\n\n"));

    }
}
