package ru.hackathon.credit.integration;

/** Подписание договора кодом. В промышленной версии код приходит через ДБО или государственный сервис подписи. */
public interface SigningProvider {

    /** Выпускает одноразовый код подтверждения. */
    String issueCode();
}
