package com.spring_ai.ai_copilot.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
public class DocumentReRankerImpl implements DocumentReranker {

    @Override
    public List<Document> rerank(
            List<String> queries,
            List<Document> documents,
            int finalTopK) {

        return documents.stream()
                .sorted(
                        Comparator.comparingInt(document -> score((Document) document,queries)).reversed()
                )
                .limit(finalTopK)
                .toList();
    }

    private int score(Document document, List<String> queries) {

        String text = document.getText();

        if (!StringUtils.hasText(text)
                || queries == null
                || queries.isEmpty()) {
            return 0;
        }

        String normalizedText = text.toLowerCase();

        return queries.stream()
                .mapToInt(query -> {
                    if (!StringUtils.hasText(query)) {
                        return 0;
                    }

                    String[] queryWords =
                            query.toLowerCase().split("\\s+");

                    return (int) Arrays.stream(queryWords)
                            .filter(normalizedText::contains)
                            .count();
                })
                .max()
                .orElse(0);
    }
}