package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.client.AiProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Component
public class MultiQueryGenerator {

    private final AiProviderClient aiProviderClient;

    private final static Logger log = LoggerFactory.getLogger(MultiQueryGenerator.class);

    public MultiQueryGenerator(AiProviderClient aiProviderClient) {
        this.aiProviderClient = aiProviderClient;
    }

    public List<String> generateMultiQuery(String query) {
        String systemPrompt = """
                You generate search queries for a RAG system.
                Generate 3 alternative search queries for the user's question.
                Each query should focus on a different important aspect,
                synonym, phrase, or concept from the user's information need.
                Try to use terminology that may appear in the source documents,
                even if that terminology is different from the user's wording.
                Do not answer the question.
                Return only the search queries, one per line.
                Do not number them.
            """;

        String response = aiProviderClient
                .getMultiQuery(systemPrompt, query)
                .message();

        log.info("MultiQueryGenerator response from LLM ={}", response);

        return Stream.concat(
                        Stream.of(query),
                        Arrays.stream(response.split("\\R"))
                                .map(String::trim)
                                .filter(StringUtils::hasText)
                )
                .limit(4)
                .toList();
    }
}
