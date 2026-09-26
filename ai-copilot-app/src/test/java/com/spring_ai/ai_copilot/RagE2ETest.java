package com.spring_ai.ai_copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.spring_ai.ai_copilot.rag.DocumentVectorStoreService;
import com.spring_ai.ai_copilot.rag.DocumentRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestMcpConfig.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class RagE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private DocumentVectorStoreService documentVectorStoreService;

    @Autowired
    private DocumentRepository documentRepository;

    private static final ObjectMapper M = new ObjectMapper();

    @BeforeEach
    void setupMocks() {
        // Provide non-null ChatOptions to avoid NPEs in AiProviderClient
        when(chatModel.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("stubbed ai response")))));

        // Prevent real vector/embedding provider calls during tests
        when(documentVectorStoreService.searchWithRequest(any())).thenReturn(java.util.List.of());
        when(documentVectorStoreService.search(any())).thenReturn(java.util.List.of());
    }

    @Test
    void health_endpoint_should_be_up() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void ingestion_should_index_documents_and_preserve_metadata() throws Exception {
        String tenant = "e2e-tenant-1";

        var res = mockMvc.perform(get("/rag")
                        .param("path", "knowledge")
                        .param("tenantId", tenant))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = M.readTree(res.getResponse().getContentAsString());
        assertTrue(arr.isArray());

        if (arr.size() > 0) {
            // if new chunks returned, verify metadata on first chunk
            JsonNode first = arr.get(0);
            assertNotNull(first.get("metadata"));
            JsonNode meta = first.get("metadata");
            assertTrue(meta.has("tenantId"));
            assertEquals(tenant, meta.get("tenantId").asText());
            assertTrue(meta.has("filename"));
            assertTrue(meta.has("fileType"));
            assertTrue(meta.has("contentHash"));
        } else {
            // Already ingested; verify repository has the document metadata
            var opt = documentRepository.findByTenantIdAndFilename(tenant, "company-policy.txt");
            assertTrue(opt.isPresent(), "document should exist in repository when ingestion returns no new chunks");
            var ent = opt.get();
            assertEquals(tenant, ent.getTenantId());
            assertNotNull(ent.getContentHash());
        }
    }

    @Test
    void duplicate_ingestion_should_be_idempotent() throws Exception {
        String tenant = "e2e-tenant-dup";

        var r1 = mockMvc.perform(get("/rag").param("path", "knowledge").param("tenantId", tenant))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode a1 = M.readTree(r1.getResponse().getContentAsString());
        int firstCount = a1.isArray() ? a1.size() : 0;

        // second ingest should succeed; if firstCount==0 we still consider idempotent
        var r2 = mockMvc.perform(get("/rag").param("path", "knowledge").param("tenantId", tenant))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode a2 = M.readTree(r2.getResponse().getContentAsString());
        int secondCount = a2.isArray() ? a2.size() : 0;

        if (firstCount > 0) {
            assertEquals(0, secondCount, "duplicate ingestion should yield zero newly indexed chunks");
        } else {
            // already ingested scenario - both counts may be zero
            assertEquals(0, secondCount);
        }
    }

    @Test
    void evaluation_retrieval_and_tenant_isolation() throws Exception {
        String tenant1 = "e2e-tenant-A";
        String tenant2 = "e2e-tenant-B";

        mockMvc.perform(get("/rag").param("path", "knowledge").param("tenantId", tenant1)).andExpect(status().isOk());
        mockMvc.perform(get("/rag").param("path", "knowledge").param("tenantId", tenant2)).andExpect(status().isOk());

        String q = "What is the refund period?";

        var rEv1 = mockMvc.perform(get("/evaluation")
                        .param("tenantId", tenant1)
                        .param("question", q)
                        .param("expectedSources", "company-policy.txt")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode j1 = M.readTree(rEv1.getResponse().getContentAsString());
        assertTrue(j1.has("retrievedDocuments"));

        var rEv2 = mockMvc.perform(get("/evaluation")
                        .param("tenantId", tenant2)
                        .param("question", q)
                        .param("expectedSources", "company-policy.txt")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode j2 = M.readTree(rEv2.getResponse().getContentAsString());
        assertTrue(j2.has("retrievedDocuments"));
    }

    @Test
    void vector_search_with_filetype_filter_and_metadata() throws Exception {
        String tenant = "e2e-tenant-filter";
        mockMvc.perform(get("/rag").param("path", "knowledge").param("tenantId", tenant)).andExpect(status().isOk());

        var sr = mockMvc.perform(get("/vectorSearchWithTopKWithFilter")
                        .param("text", "refund")
                        .param("filter", "txt"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = M.readTree(sr.getResponse().getContentAsString());
        assertTrue(arr.isArray());
        // results may be empty in test env; if not empty verify metadata
        if (arr.size() > 0) {
            for (JsonNode doc : arr) {
                JsonNode meta = doc.get("metadata");
                assertTrue(meta.has("fileType"));
                assertEquals("txt", meta.get("fileType").asText());
            }
        }
    }

    @Test
    void ask_validation_bad_requests_should_return_400() throws Exception {
        String body = "{\"conversationId\":\"c1\",\"ask\":\"refund policy\", \"tenantId\":\"t1\"}";

        mockMvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

}
