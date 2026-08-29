package com.spring_ai.ai_copilot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class AiExceptionTranslator {

    private static final List<ProviderErrorMapping> PROVIDER_ERROR_MAPPINGS = List.of(

            // BulkheadFullException

            new ProviderErrorMapping(
                    "io.github.resilience4j.bulkhead.BulkheadFullException",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_BUSY",
                    "AI provider is busy, please try again later"
            ),

            // Rate limit exceeded
            new ProviderErrorMapping(
                    "io.github.resilience4j.ratelimiter.RequestNotPermitted",
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_RATE_LIMIT_EXCEEDED",
                    "AI request rate limit exceeded"
            ),

            // Circuit Breaker OPEN
            new ProviderErrorMapping(
                    "io.github.resilience4j.circuitbreaker.CallNotPermittedException",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE",
                    "AI provider is temporarily unavailable"
            ),

            // Authentication
            new ProviderErrorMapping(
                    "com.openai.errors.UnauthorizedException",
                    HttpStatus.UNAUTHORIZED,
                    "AI_PROVIDER_AUTH_ERROR",
                    "AI provider authentication failed"
            ),

            new ProviderErrorMapping(
                    "com.openai.errors.AuthenticationException",
                    HttpStatus.UNAUTHORIZED,
                    "AI_PROVIDER_AUTH_ERROR",
                    "AI provider authentication failed"
            ),

            // Bad request
            new ProviderErrorMapping(
                    "com.openai.errors.BadRequestException",
                    HttpStatus.BAD_REQUEST,
                    "AI_PROVIDER_BAD_REQUEST",
                    "AI provider rejected the request"
            ),

            // Not found
            new ProviderErrorMapping(
                    "com.openai.errors.NotFoundException",
                    HttpStatus.NOT_FOUND,
                    "AI_PROVIDER_NOT_FOUND",
                    "AI provider resource not found"
            ),

            // Forbidden
            new ProviderErrorMapping(
                    "com.openai.errors.PermissionDeniedException",
                    HttpStatus.FORBIDDEN,
                    "AI_PROVIDER_FORBIDDEN",
                    "AI provider access denied"
            ),

            // Rate limit
            new ProviderErrorMapping(
                    "com.openai.errors.RateLimitException",
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_PROVIDER_RATE_LIMIT",
                    "AI provider rate limit exceeded"
            ),

            // Conflict
            new ProviderErrorMapping(
                    "com.openai.errors.ConflictException",
                    HttpStatus.CONFLICT,
                    "AI_PROVIDER_CONFLICT",
                    "AI provider conflict encountered"
            ),

            // Generic OpenAI API error
            new ProviderErrorMapping(
                    "com.openai.errors.APIException",
                    HttpStatus.BAD_GATEWAY,
                    "AI_PROVIDER_API_ERROR",
                    "AI provider returned an API error"
            ),

            // Generic network/I/O error
            new ProviderErrorMapping(
                    "com.openai.errors.OpenAIIoException",
                    HttpStatus.BAD_GATEWAY,
                    "AI_PROVIDER_NETWORK_ERROR",
                    "AI provider network request failed"
            ),

            // Retryable provider error
            new ProviderErrorMapping(
                    "com.openai.errors.OpenAIRetryableException",
                    HttpStatus.BAD_GATEWAY,
                    "AI_PROVIDER_RETRYABLE_ERROR",
                    "AI provider request failed and may be retried"
            )
    );

    public AiProviderException translate(Exception exception) {

        /*
         * Timeout must be checked first because the actual timeout
         * may be wrapped inside OpenAIIoException.
         */
        if (isTimeout(exception)) {
            return new AiProviderException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI_PROVIDER_TIMEOUT",
                    "AI provider request timed out",
                    exception
            );
        }

        /*
         * Check known provider-specific exceptions.
         */
        ProviderErrorMapping mapping = resolveErrorMapping(exception);

        if (mapping != null) {
            return new AiProviderException(
                    mapping.status(),
                    mapping.errorCode(),
                    mapping.message(),
                    exception
            );
        }

        /*
         * Unknown AI/provider exception.
         */
        return new AiProviderException(
                HttpStatus.BAD_GATEWAY,
                "AI_PROVIDER_ERROR",
                "AI provider request failed",
                exception
        );
    }

    /**
     * Walk through the complete exception cause chain and
     * determine whether the failure is actually a timeout.
     */
    private boolean isTimeout(Throwable throwable) {

        for (Throwable current = throwable;
             current != null;
             current = current.getCause()) {

            if (current instanceof SocketTimeoutException) {
                return true;
            }

            if (current instanceof TimeoutException) {
                return true;
            }

            if (current instanceof InterruptedIOException
                    && "timeout".equalsIgnoreCase(current.getMessage())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Walk through the complete cause chain and find
     * a matching provider-specific exception.
     */
    private ProviderErrorMapping resolveErrorMapping(Throwable throwable) {

        for (Throwable current = throwable;
             current != null;
             current = current.getCause()) {

            for (ProviderErrorMapping mapping : PROVIDER_ERROR_MAPPINGS) {

                if (mapping.matches(current)) {
                    return mapping;
                }
            }
        }

        return null;
    }

    private record ProviderErrorMapping(
            String className,
            HttpStatus status,
            String errorCode,
            String message
    ) {

        private boolean matches(Throwable throwable) {

            try {

                Class<?> providerExceptionType =
                        Class.forName(className);

                return providerExceptionType.isInstance(throwable);

            } catch (ClassNotFoundException e) {

                return false;
            }
        }
    }
}