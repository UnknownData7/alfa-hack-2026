package ru.hackathon.credit.assistant;

import java.util.Locale;

/** Форматирование чисел в ответах помощника так же, как в интерфейсе кабинета. */
public final class AssistantFormat {

    private static final Locale RU = Locale.forLanguageTag("ru");

    private AssistantFormat() {
    }

    public static String money(double value) {
        // Неразрывный пробел между разрядами, как в Intl.NumberFormat на клиенте
        return String.format(RU, "%,.0f ₽", value).replace(' ', ' ').replace(' ', ' ');
    }

    public static String pct(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(RU, "%.1f", value);
    }

    public static String months(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        String word = mod10 == 1 && mod100 != 11 ? "месяц"
                : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "месяца" : "месяцев";
        return n + " " + word;
    }
}
