package ru.hackathon.credit.common;

import java.time.Duration;

/** Превышен лимит попыток; ответ 429 с заголовком Retry-After. */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
