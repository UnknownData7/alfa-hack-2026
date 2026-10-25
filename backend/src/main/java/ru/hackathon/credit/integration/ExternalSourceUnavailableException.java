package ru.hackathon.credit.integration;

/** Внешний источник не ответил или ответил ошибкой. Заявка при этом не теряется, а уходит на ручную проверку. */
public class ExternalSourceUnavailableException extends RuntimeException {

    private final String source;

    public ExternalSourceUnavailableException(String source, String message) {
        super(message);
        this.source = source;
    }

    public String source() {
        return source;
    }
}
