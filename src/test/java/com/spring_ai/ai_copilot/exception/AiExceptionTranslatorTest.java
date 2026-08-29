package com.spring_ai.ai_copilot.exception;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiExceptionTranslatorTest {

    private final AiExceptionTranslator aiExceptionTranslator = new AiExceptionTranslator();

    @Test
    void shouldTranslateTimeoutException() {
        AiProviderException response = aiExceptionTranslator.translate(new SocketTimeoutException());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatus());
        assertEquals("AI_PROVIDER_TIMEOUT", response.getErrorCode());
        assertEquals("AI provider request timed out", response.getMessage());

        response = aiExceptionTranslator.translate(new TimeoutException());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatus());
        assertEquals("AI_PROVIDER_TIMEOUT", response.getErrorCode());
        assertEquals("AI provider request timed out", response.getMessage());

        response = aiExceptionTranslator.translate(new InterruptedIOException("timeout"));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatus());
        assertEquals("AI_PROVIDER_TIMEOUT", response.getErrorCode());
        assertEquals("AI provider request timed out", response.getMessage());
    }

    @Test
    void shouldTranslateBulkheadFullException() {
        AiProviderException response = aiExceptionTranslator.translate(
                BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("demo"))
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals("AI_PROVIDER_BUSY", response.getErrorCode());
        assertEquals("AI provider is busy, please try again later", response.getMessage());
    }

    @Test
    void shouldTranslateRateLimitAndCircuitBreakerExceptions() {
        AiProviderException response = aiExceptionTranslator.translate(
                io.github.resilience4j.ratelimiter.RequestNotPermitted.createRequestNotPermitted(
                        io.github.resilience4j.ratelimiter.RateLimiter.ofDefaults("demo")
                )
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatus());
        assertEquals("AI_RATE_LIMIT_EXCEEDED", response.getErrorCode());
        assertEquals("AI request rate limit exceeded", response.getMessage());

        response = aiExceptionTranslator.translate(
                io.github.resilience4j.circuitbreaker.CallNotPermittedException.createCallNotPermittedException(
                        io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("demo")
                )
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals("AI_PROVIDER_UNAVAILABLE", response.getErrorCode());
        assertEquals("AI provider is temporarily unavailable", response.getMessage());
    }

    @Test
    void shouldTranslateUnknownExceptionAsGenericProviderError() {
        AiProviderException response = aiExceptionTranslator.translate(new RuntimeException("boom"));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatus());
        assertEquals("AI_PROVIDER_ERROR", response.getErrorCode());
        assertEquals("AI provider request failed", response.getMessage());
    }
}
