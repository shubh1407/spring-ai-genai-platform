package com.spring_ai.ai_copilot.rag;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DocumentRegistry {

    private final Set<String> ingestedDocuments = new HashSet<>();

    public boolean exists(String tenantId, String fileName, String contentHash) {
        String key = buildKey(tenantId, fileName, contentHash);
        return ingestedDocuments.contains(key);
    }

    public void register(String tenantId, String fileName, String contentHash) {
        String key = buildKey(tenantId, fileName, contentHash);
        ingestedDocuments.add(key);
    }

    private String buildKey(String tenantId, String fileName, String contentHash) {
        return tenantId + ":" + fileName + ":" + contentHash;
    }
}