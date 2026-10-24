package ru.hackathon.credit.assistant;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Извлечение суммы и срока из текста клиента: «400 000», «1,2 млн», «400к», «полгода», «2 года». */
public final class TextParsers {

    private static final Pattern NUMBER = Pattern.compile(
            "(\\d{1,3}(?:[ \\u00a0]\\d{3})+|\\d+)(?:[.,](\\d+))?\\s*([\\p{L}%]*)", Pattern.UNICODE_CHARACTER_CLASS);

    private TextParsers() {
    }

    /**
     * Сумма в рублях. Числа, за которыми идут месяцы, годы или проценты, пропускаются. Число меньше тысячи без
     * единиц понимается как тысячи рублей: клиенты часто пишут «400», имея в виду 400 тысяч.
     */
    public static Optional<Long> amount(String text) {
        String t = Guardrails.normalize(text);
        Matcher m = NUMBER.matcher(t);
        while (m.find()) {
            String unit = m.group(3);
            if (unit.startsWith("мес") || unit.startsWith("год") || unit.startsWith("лет") || unit.startsWith("дн")
                    || unit.startsWith("нед") || unit.startsWith("%") || unit.startsWith("процент")) {
                continue;
            }
            double value = number(m);
            if (unit.startsWith("млн") || unit.startsWith("миллион") || unit.equals("м")) {
                value *= 1_000_000;
            } else if (unit.startsWith("тыс") || unit.equals("т") || unit.equals("к") || unit.equals("k")) {
                value *= 1_000;
            } else if (value < 1_000) {
                value *= 1_000;
            }
            return value > 0 ? Optional.of(Math.round(value)) : Optional.empty();
        }
        if (t.contains("полтора милл")) {
            return Optional.of(1_500_000L);
        }
        if (t.matches("(?U).*\\bмиллион.*")) {
            return Optional.of(1_000_000L);
        }
        return Optional.empty();
    }

    /** Срок в месяцах от 1 до 120. Голое число понимается как месяцы. */
    public static Optional<Integer> months(String text) {
        String t = Guardrails.normalize(text).trim();
        if (t.contains("полгода")) {
            return Optional.of(6);
        }
        if (t.contains("полтора года")) {
            return Optional.of(18);
        }
        Matcher m = NUMBER.matcher(t);
        while (m.find()) {
            String unit = m.group(3);
            double value = number(m);
            Integer months = null;
            if (unit.startsWith("мес")) {
                months = (int) Math.round(value);
            } else if (unit.startsWith("год") || unit.startsWith("лет")) {
                months = (int) Math.round(value * 12);
            } else if (unit.isEmpty() && m.start() == 0 && m.end() == t.length()) {
                months = (int) Math.round(value);
            }
            if (months != null) {
                return months >= 1 && months <= 120 ? Optional.of(months) : Optional.empty();
            }
        }
        if (t.matches("(?U).*\\bгод\\b.*")) {
            return Optional.of(12);
        }
        return Optional.empty();
    }

    private static double number(Matcher m) {
        String whole = m.group(1).replaceAll("[ \\u00a0]", "");
        String fraction = m.group(2);
        return Double.parseDouble(fraction == null ? whole : whole + "." + fraction);
    }
}
