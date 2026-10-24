package ru.hackathon.credit.assistant;

/**
 * Шаг разговора. Определяет, как понимать свободный текст и какие кнопки показать. Вопрос в свободной форме и отказ
 * по теме не меняют шаг: после ответа клиент продолжает с того места, где был.
 */
public enum DialogState {
    GREETING,
    /** Ждём цель своими словами. */
    PURPOSE,
    /** Уточняющий вопрос по цели, из ответа берётся подсказка по сроку. */
    PURPOSE_DETAIL,
    AMOUNT,
    TERM,
    SUMMARY,
    PRODUCTS,
    COMPARE,
    WIZARD_HELP,
    APPLICATION_HELP
}
