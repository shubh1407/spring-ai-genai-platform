package com.spring_ai.ai_copilot.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

import java.io.IOException;

public class GroqReasoningInterceptor implements Interceptor {

    private final ObjectMapper objectMapper;

    public GroqReasoningInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {

        Request request = chain.request();
        RequestBody requestBody = request.body();

        if (requestBody == null) {
            return chain.proceed(request);
        }

        Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);

        String json = buffer.readUtf8();

        JsonNode root = objectMapper.readTree(json);
        JsonNode messages = root.get("messages");

        if (messages != null && messages.isArray()) {

            for (JsonNode message : messages) {

                if (message instanceof ObjectNode assistant
                        && "assistant".equals(
                        assistant.path("role").asText())) {

                    assistant.remove("reasoning_content");
                    assistant.remove("reasoning");
                }
            }
        }

        String modifiedJson =
                objectMapper.writeValueAsString(root);

        Request modifiedRequest = request.newBuilder()
                .method(
                        request.method(),
                        RequestBody.create(
                                modifiedJson,
                                requestBody.contentType()
                        )
                )
                .build();

        return chain.proceed(modifiedRequest);
    }
}