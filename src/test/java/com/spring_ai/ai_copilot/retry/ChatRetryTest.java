package com.spring_ai.ai_copilot.retry;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatRetryTest {

    @Test
    void shouldRetryTransientAiException() {
        AtomicInteger attempts = new AtomicInteger();

        ChatModel fakeChatModel = prompt -> {
            int currentAttempt = attempts.incrementAndGet();
            System.out.println("AI ATTEMPT = " + currentAttempt);

            if (currentAttempt < 3) {
                throw new TransientAiException("Temporary AI failure");
            }

            return new ChatResponse(
                    java.util.List.of(new Generation(new AssistantMessage("Success after retry")))
            );
        };

        ChatClient chatClient = ChatClient.builder(fakeChatModel)
                .build();

        String result = RetryUtils.execute(RetryUtils.SHORT_RETRY_TEMPLATE, () ->
                chatClient.prompt()
                        .user("Test retry")
                        .call()
                        .content()
        );

        assertEquals("Success after retry", result);
        assertEquals(3, attempts.get());
    }
}
