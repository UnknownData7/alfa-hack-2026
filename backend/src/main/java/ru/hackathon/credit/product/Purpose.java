package ru.hackathon.credit.product;

/** Цель финансирования простыми словами. */
public enum Purpose {
    WORKING_CAPITAL("Пополнение оборотных средств"),
    CASH_GAP("Кассовый разрыв"),
    PURCHASE("Закупка товара или сырья"),
    EQUIPMENT("Покупка оборудования"),
    TRANSPORT("Покупка транспорта"),
    NEW_DIRECTION("Запуск нового направления");

    private final String label;

    Purpose(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
