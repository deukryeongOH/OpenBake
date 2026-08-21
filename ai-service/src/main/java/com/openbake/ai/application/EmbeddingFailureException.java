package com.openbake.ai.application;

import java.time.Duration;
import lombok.Getter;

@Getter
public class EmbeddingFailureException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;
    private final Duration retryAfter;

    public EmbeddingFailureException(String errorCode, boolean retryable, Duration retryAfter, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public static EmbeddingFailureException transientFailure(String errorCode, Throwable cause) {
        return new EmbeddingFailureException(errorCode, true, null, cause);
    }

    public static EmbeddingFailureException transientFailure(
            String errorCode, Duration retryAfter, Throwable cause) {
        return new EmbeddingFailureException(errorCode, true, retryAfter, cause);
    }

    public static EmbeddingFailureException permanentFailure(String errorCode, Throwable cause) {
        return new EmbeddingFailureException(errorCode, false, null, cause);
    }
}
