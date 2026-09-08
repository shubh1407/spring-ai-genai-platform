package com.spring_ai.ai_copilot.rag;

import com.spring_ai.ai_copilot.client.AiProviderClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class MultiQueryGenerator {

    private final AiProviderClient aiProviderClient;
    private final MeterRegistry meterRegistry;

    private final static Logger log = LoggerFactory.getLogger(MultiQueryGenerator.class);

    public MultiQueryGenerator(AiProviderClient aiProviderClient, MeterRegistry meterRegistry) {
        this.aiProviderClient = aiProviderClient;
        this.meterRegistry = meterRegistry;
    }

    public List<String> generateMultiQuery(String query) {
        long startedAt = System.nanoTime();
        log.info("rag.multi.query.started operation=multi.query inputLength={}", query == null ? 0 : query.length());
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

        try {
            String response = aiProviderClient
                    .getMultiQuery(systemPrompt, query)
                    .message();

            List<String> queries = Stream.concat(
                            Stream.of(query),
                            Arrays.stream(response.split("\\R"))
                                    .map(String::trim)
                                    .filter(StringUtils::hasText)
                    )
                    .limit(4)
                    .toList();

            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.multi.query.count", "status", "success").increment();
            meterRegistry.timer("rag.multi.query.duration", "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
            log.info("rag.multi.query.completed operation=multi.query durationMs={} count={} status=success", durationMs, queries.size());
            return queries;
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            meterRegistry.counter("rag.multi.query.count", "status", "failure").increment();
            meterRegistry.timer("rag.multi.query.duration", "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
            log.warn("rag.multi.query.failed operation=multi.query durationMs={} exception={}", durationMs, e.getClass().getSimpleName(), e);
            throw e;
        }
    }
}
