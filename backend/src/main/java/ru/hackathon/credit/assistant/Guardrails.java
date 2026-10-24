package ru.hackathon.credit.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Удержание помощника в теме без модели: словари и правила для входящего текста и проверка исходящего ответа.
 * Порядок правил важен: попытка изменить правила и обход проверок отсекаются раньше остальных.
 */
public final class Guardrails {

    private static final Map<String, Pattern> INPUT_RULES = new LinkedHashMap<>();

    static {
        INPUT_RULES.put("policy.injection", re(
                "(забудь|игнорируй|проигнорируй|отмени)\\w*\\s+(\\w+\\s+){0,3}(инструкци|правил|указани|ограничени)",
                "системн\\w*\\s+(промпт|инструкци|сообщени)", "\\bprompt", "\\bты\\s+теперь\\b", "веди\\s+себя\\s+как",
                "представь,?\\s+что\\s+ты", "jailbreak", "developer\\s+mode", "ignore\\s+(all|previous)"));
        INPUT_RULES.put("policy.bypass", re("\\bобойти", "\\bобману", "\\bобмануть", "\\bскрыть",
                "подделать|поддельн|фиктивн|фальшив", "завысить|занизить"));
        INPUT_RULES.put("policy.decision", re("\\bбалл", "скоринг", "\\bгрейд",
                "почему\\s+(мне\\s+)?(отказ|не\\s+одобр|отклон)", "причин\\w*\\s+(отказ|решени)", "внутренн\\w*\\s+оценк",
                "как\\s+банк\\s+оценива"));
        INPUT_RULES.put("policy.guarantee", re("точно\\s+одобр", "гарантир", "\\b100\\s?%", "обязательно\\s+одобр",
                "одобрят\\s+ли", "дадут\\s+ли", "шанс\\w*\\s+(на\\s+)?одобрен", "вероятност\\w*\\s+одобрен"));
        INPUT_RULES.put("policy.contact", re("\\bпозвон", "\\bтелефон", "\\bменеджер", "\\bпочт(а|у|ой|е)\\b",
                "\\be-?mail", "\\bтелеграм", "\\btelegram", "whats\\s?app", "ватсап|вотсап|вацап", "\\bсмс\\b",
                "\\bsms\\b", "\\b(в|во)\\s+(офис|отделени)", "\\bприехать\\s+в\\s+банк"));
        INPUT_RULES.put("policy.off-topic", re("\\bстих", "анекдот", "\\bшутк", "\\bпогод",
                "курс\\w*\\s+(доллар|евро|валют|юан)", "биткоин|криптовалют", "политик", "рецепт", "футбол|хоккей",
                "\\bфильм|\\bсериал", "сочини|сочинение", "\\bпереведи", "конкурент", "(другом|других|чужом|чужих)\\s+банк",
                "напиши\\s+(код|программ)"));
    }

    /** Слова, по которым вопрос считается относящимся к кредиту, даже если в базе знаний нет ответа. */
    private static final Pattern ON_TOPIC = re("кредит", "заявк", "ставк", "процент", "плат[её]ж", "переплат", "сумм",
            "\\bсрок", "\\bбанк", "деньг", "денег", "займ", "заем", "продукт", "документ", "залог", "овердрафт",
            "экспресс", "оборуд", "выписк", "договор", "подпис", "одобр", "предложени", "оборот", "бизнес", "лимит",
            "погаш", "бюро", "истори");

    /** Чего не должно быть в ответе: обещаний решения, внутренних терминов оценки, каналов вне кабинета. */
    private static final Pattern FORBIDDEN_OUTPUT = re("точно\\s+одобр", "одобрение\\s+гарантир", "гарантируем",
            "\\bбалл", "скоринг", "\\bгрейд", "позвоните", "напишите\\s+(нам\\s+)?на\\s+почту", "в\\s+мессенджер");

    private Guardrails() {
    }

    /** Фрагмент из раздела policy, если вопрос нужно отклонить. */
    public static Optional<String> policyFor(String text) {
        String t = normalize(text);
        for (Map.Entry<String, Pattern> rule : INPUT_RULES.entrySet()) {
            if (rule.getValue().matcher(t).find()) {
                return Optional.of(rule.getKey());
            }
        }
        return Optional.empty();
    }

    public static boolean onTopic(String text) {
        return ON_TOPIC.matcher(normalize(text)).find();
    }

    /** Найденные запрещённые формулировки в ответе; пусто, если ответ можно показать. */
    public static List<String> violations(String reply) {
        List<String> found = new ArrayList<>();
        var m = FORBIDDEN_OUTPUT.matcher(normalize(reply));
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replace('ё', 'е').replace(' ', ' ');
    }

    private static Pattern re(String... parts) {
        return Pattern.compile(String.join("|", parts), Pattern.UNICODE_CHARACTER_CLASS);
    }
}
