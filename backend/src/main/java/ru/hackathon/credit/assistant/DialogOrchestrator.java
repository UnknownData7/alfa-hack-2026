package ru.hackathon.credit.assistant;

import static ru.hackathon.credit.assistant.AssistantFormat.money;
import static ru.hackathon.credit.assistant.AssistantFormat.months;
import static ru.hackathon.credit.assistant.AssistantFormat.pct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.hackathon.credit.application.ApplicationDtos.NextStep;
import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.application.ApplicationViews;
import ru.hackathon.credit.assistant.AssistantDtos.ActionRequest;
import ru.hackathon.credit.assistant.AssistantDtos.Button;
import ru.hackathon.credit.assistant.AssistantDtos.MessageRequest;
import ru.hackathon.credit.assistant.AssistantDtos.Page;
import ru.hackathon.credit.assistant.AssistantDtos.PageContext;
import ru.hackathon.credit.assistant.AssistantDtos.Reply;
import ru.hackathon.credit.assistant.AssistantDtos.Slots;
import ru.hackathon.credit.assistant.AssistantDtos.TermOption;
import ru.hackathon.credit.assistant.AssistantDtos.UiAction;
import ru.hackathon.credit.assistant.kb.KnowledgeBase;
import ru.hackathon.credit.assistant.kb.KnowledgeChunk;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.pricing.PricingCalculator;
import ru.hackathon.credit.product.ProductMatcher.Recommendation;
import ru.hackathon.credit.product.Purpose;

/**
 * Сценарный помощник: машина состояний разговора без языковой модели. Цифры берутся только из подбора продуктов и
 * калькулятора платежа, формулировки из базы знаний и шаблонов ниже. Переходы между шагами делает только оркестратор,
 * изменения в заявке выполняет клиент сам: помощник лишь подставляет значения в форму и открывает мастер.
 */
@Service
public class DialogOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DialogOrchestrator.class);

    public enum Action {
        START, PURPOSE, PURPOSE_FREE, HORIZON, AMOUNT, AMOUNT_HELP, CHANGE_AMOUNT, TERM, TERM_HELP, CHANGE_TERM,
        FILL_FORM, COMPARE, SELECT_PRODUCT, KB, GO_NEED, HIGHLIGHT
    }

    /** Цели в бытовых формулировках, в порядке показа на кнопках. */
    private static final Map<Purpose, String> EVERYDAY = new LinkedHashMap<>();

    static {
        EVERYDAY.put(Purpose.PURCHASE, "Закупить товар");
        EVERYDAY.put(Purpose.CASH_GAP, "Закрыть разрыв до оплаты от клиентов");
        EVERYDAY.put(Purpose.WORKING_CAPITAL, "Текущие расходы бизнеса");
        EVERYDAY.put(Purpose.EQUIPMENT, "Купить оборудование");
        EVERYDAY.put(Purpose.TRANSPORT, "Купить транспорт");
        EVERYDAY.put(Purpose.NEW_DIRECTION, "Открыть новое направление");
    }

    /** Уточняющий вопрос по цели: из ответа получается подсказка по сроку в месяцах. */
    private record Detail(String question, List<String> labels, List<Integer> months) {
    }

    private static final Map<Purpose, Detail> DETAILS = Map.of(
            Purpose.CASH_GAP, new Detail("Через сколько обычно приходят деньги от клиентов?",
                    List.of("До месяца", "1–3 месяца", "Дольше"), List.of(3, 6, 12)),
            Purpose.PURCHASE, new Detail("За какое время обычно продаётся закупленный товар?",
                    List.of("До 3 месяцев", "3–6 месяцев", "Дольше"), List.of(6, 9, 12)),
            Purpose.WORKING_CAPITAL, new Detail("За какое время бизнес вернёт эти деньги из выручки?",
                    List.of("До полугода", "Около года"), List.of(6, 12)),
            Purpose.EQUIPMENT, new Detail("Сколько лет будете пользоваться оборудованием?",
                    List.of("1–2 года", "3–5 лет", "Дольше"), List.of(24, 36, 60)),
            Purpose.TRANSPORT, new Detail("Сколько лет будете пользоваться транспортом?",
                    List.of("1–2 года", "3–5 лет", "Дольше"), List.of(24, 36, 60)),
            Purpose.NEW_DIRECTION, new Detail("Через сколько новое направление начнёт приносить деньги?",
                    List.of("До года", "1–2 года", "Дольше"), List.of(12, 36, 60)));

    private static final Map<Purpose, String> AMOUNT_QUESTIONS = Map.of(
            Purpose.CASH_GAP, "Сколько не хватает, чтобы закрыть расходы до поступления денег? Можно примерно.",
            Purpose.PURCHASE, "На какую сумму планируете закупку?",
            Purpose.WORKING_CAPITAL, "Сколько нужно на текущие расходы? Можно примерно.",
            Purpose.EQUIPMENT, "Сколько стоит оборудование без учёта денег, которые вложите сами?",
            Purpose.TRANSPORT, "Сколько стоит транспорт без учёта вашей части?",
            Purpose.NEW_DIRECTION, "Сколько не хватает на запуск?");

    private static final String FALLBACK = "Не могу ответить на это. Я помогаю подобрать и оформить кредит для бизнеса.";

    private final KnowledgeBase kb;
    private final AssistantTools tools;

    public DialogOrchestrator(KnowledgeBase kb, AssistantTools tools) {
        this.kb = kb;
        this.tools = tools;
    }

    /** Шаг разговора, который собирается в ответ. */
    private static final class Turn {
        DialogState state;
        Slots slots;
        final PageContext page;
        final List<String> paragraphs = new ArrayList<>();
        List<TermOption> terms = List.of();
        final List<Button> buttons = new ArrayList<>();
        final List<UiAction> ui = new ArrayList<>();
        final List<String> sources = new ArrayList<>();

        Turn(DialogState state, Slots slots, PageContext page) {
            this.state = state;
            this.slots = slots;
            this.page = page;
        }

        void say(String text) {
            paragraphs.add(text);
        }

        void button(String label, Action action, String... params) {
            buttons.add(new Button(label, action.name(), params(params)));
        }

        void ui(String type, String... params) {
            ui.add(new UiAction(type, params(params)));
        }

        void chunk(KnowledgeChunk c) {
            say(c.body());
            sources.add(c.id());
        }

        Page pageKind() {
            return page == null || page.page() == null ? Page.OTHER : page.page();
        }
    }

    public Reply message(MessageRequest r) {
        Turn t = new Turn(r.state() == null ? DialogState.GREETING : r.state(), merge(r.slots(), r.page(), false), r.page());
        String text = r.text().strip();
        Optional<String> policy = Guardrails.policyFor(text);
        if (policy.isPresent()) {
            kb.get(policy.get()).ifPresent(t::chunk);
            if (policy.get().equals("policy.off-topic") || policy.get().equals("policy.injection")) {
                t.button("Подобрать кредит", Action.START, "topic", "purpose");
            } else {
                contextButtons(t);
            }
            return finish(t);
        }
        String plain = Guardrails.normalize(text).replaceAll("[^\\p{L} ]", "").strip();
        if (plain.matches("(?U)(привет|здравствуй\\w*|добрый (день|вечер)|доброе утро|hello|hi)\\b.{0,20}")) {
            askPurpose(t, "Здравствуйте. Помогу подобрать и оформить кредит для бизнеса. Для чего нужны деньги?");
            return finish(t);
        }
        if (!text.matches(".*\\d.*") && plain.matches("(?U)(спасибо|благодарю|понятно|ясно|ок|хорошо)\\b.{0,20}")) {
            t.say("Пожалуйста. Если появятся вопросы, я здесь.");
            contextButtons(t);
            return finish(t);
        }
        if (understoodInFlow(t, text)) {
            return finish(t);
        }
        List<KnowledgeChunk> hits = kb.search(text, KnowledgeBase.ANSWER_SOURCES, 1);
        if (!hits.isEmpty()) {
            t.chunk(hits.get(0));
            contextButtons(t);
        } else if (t.state == DialogState.PURPOSE) {
            askPurpose(t, "Не до конца понял задачу. Выберите ближайший вариант или опишите подробнее.");
        } else if (Guardrails.onTopic(text)) {
            t.say("Не нашёл точного ответа на этот вопрос. Попробуйте спросить по-другому или выберите тему.");
            t.button("Как оформить кредит", Action.KB, "id", "process.steps");
            t.button("Как выбрать срок", Action.TERM_HELP);
            t.button("Какие документы нужны", Action.KB, "id", "process.documents");
            t.button("Что будет после отправки", Action.KB, "id", "process.after-submit");
        } else {
            kb.get("policy.off-topic").ifPresent(t::chunk);
            t.button("Подобрать кредит", Action.START, "topic", "purpose");
        }
        return finish(t);
    }

    public Reply action(ActionRequest r) {
        Action action;
        try {
            action = Action.valueOf(r.action());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("UNKNOWN_ACTION", "Неизвестное действие помощника");
        }
        Map<String, String> p = r.params() == null ? Map.of() : r.params();
        String topic = p.get("topic");
        boolean fromPage = action == Action.START && ("term".equals(topic) || "compare".equals(topic));
        Turn t = new Turn(r.state() == null ? DialogState.GREETING : r.state(), merge(r.slots(), r.page(), fromPage), r.page());
        switch (action) {
            case START -> start(t, topic);
            case PURPOSE -> purposeChosen(t, purpose(p.get("purpose")), null);
            case PURPOSE_FREE -> {
                t.state = DialogState.PURPOSE;
                t.say("Опишите задачу своими словами, например: «нужно оплатить аренду, пока не пришли деньги от заказчика». "
                        + "Я подскажу, как это называется в банке.");
            }
            case HORIZON -> {
                t.slots = t.slots.withTermHint(intParam(p, "months", 1, 120));
                askAmount(t, null);
            }
            case AMOUNT -> amountGiven(t, longParam(p, "amount"), null);
            case AMOUNT_HELP -> {
                kb.get("faq.amount").ifPresent(t::chunk);
                askAmount(t, null);
            }
            case CHANGE_AMOUNT -> askAmount(t, null);
            case TERM -> summary(t, intParam(p, "term", 1, 120));
            case TERM_HELP -> termHelp(t);
            case CHANGE_TERM -> changeTerm(t);
            case FILL_FORM -> fillForm(t, p);
            case COMPARE -> {
                if (p.containsKey("term")) {
                    t.slots = t.slots.withTerm(intParam(p, "term", 1, 120));
                }
                compare(t);
            }
            case SELECT_PRODUCT -> selectProduct(t, p.get("product"));
            case KB -> {
                KnowledgeChunk c = kb.get(p.getOrDefault("id", ""))
                        .filter(x -> KnowledgeBase.ANSWER_SOURCES.contains(x.source()))
                        .orElseThrow(() -> ApiException.badRequest("UNKNOWN_TOPIC", "Нет такого раздела подсказок"));
                t.chunk(c);
                contextButtons(t);
            }
            case GO_NEED -> {
                t.ui("NAVIGATE", "to", "/need");
                t.slots = Slots.EMPTY;
                askPurpose(t, "Подберём кредит заново. Для чего нужны деньги?");
            }
            case HIGHLIGHT -> {
                String target = p.getOrDefault("target", "");
                if (!target.matches("[a-z-]{1,40}")) {
                    throw ApiException.badRequest("UNKNOWN_TARGET", "Неизвестный блок страницы");
                }
                t.ui("HIGHLIGHT", "target", target);
            }
        }
        return finish(t);
    }

    // --- Шаги подбора

    private void start(Turn t, String topic) {
        if (topic == null) {
            topic = switch (t.pageKind()) {
                case WIZARD -> "wizard";
                case APPLICATION -> "application";
                default -> "purpose";
            };
        }
        switch (topic) {
            case "term" -> termHelp(t);
            case "compare" -> compare(t);
            case "amount" -> {
                kb.get("faq.amount").ifPresent(t::chunk);
                askAmount(t, null);
            }
            case "wizard" -> wizardHelp(t);
            case "application" -> applicationHelp(t);
            default -> askPurpose(t, null);
        }
    }

    private void askPurpose(Turn t, String intro) {
        t.state = DialogState.PURPOSE;
        t.say(intro != null ? intro : "Помогу подобрать кредит. Для чего нужны деньги?");
        EVERYDAY.forEach((purpose, label) -> t.button(label, Action.PURPOSE, "purpose", purpose.name()));
        t.button("Не знаю, расскажу сам", Action.PURPOSE_FREE);
    }

    private void purposeChosen(Turn t, Purpose purpose, String intro) {
        t.slots = new Slots(purpose, null, null, null, null);
        Detail d = DETAILS.get(purpose);
        t.state = DialogState.PURPOSE_DETAIL;
        t.say(intro == null ? d.question() : intro + " " + d.question());
        for (int i = 0; i < d.labels().size(); i++) {
            t.button(d.labels().get(i), Action.HORIZON, "months", String.valueOf(d.months().get(i)));
        }
    }

    private void askAmount(Turn t, String intro) {
        if (t.slots.purpose() == null) {
            askPurpose(t, null);
            return;
        }
        t.state = DialogState.AMOUNT;
        String question = AMOUNT_QUESTIONS.get(t.slots.purpose()) + " Напишите сумму в рублях.";
        t.say(intro == null ? question : intro + " " + question);
        for (long a : longTerm(t.slots.purpose()) ? new long[] {1_000_000, 3_000_000, 5_000_000}
                : new long[] {300_000, 500_000, 1_000_000}) {
            t.button(money(a), Action.AMOUNT, "amount", String.valueOf(a));
        }
        t.button("Помогите оценить", Action.AMOUNT_HELP);
    }

    private void amountGiven(Turn t, long amount, String intro) {
        t.slots = t.slots.withAmount(amount);
        if (t.slots.purpose() == null) {
            askPurpose(t, "Понял, нужно " + money(amount) + ". Для чего нужны деньги?");
            return;
        }
        if (intro != null) {
            t.say(intro);
        }
        offerTerms(t);
    }

    private void termHelp(Turn t) {
        kb.get("glossary.term").ifPresent(t::chunk);
        if (t.slots.purpose() != null && t.slots.amount() != null) {
            offerTerms(t);
        } else {
            t.button("Подобрать сумму и срок", Action.START, "topic", "purpose");
        }
    }

    private void changeTerm(Turn t) {
        if (t.slots.purpose() == null || t.slots.amount() == null) {
            askAmount(t, null);
        } else {
            offerTerms(t);
        }
    }

    /** Два-три варианта срока у самого подходящего продукта с ориентировочным платежом и переплатой. */
    private void offerTerms(Turn t) {
        Purpose purpose = t.slots.purpose();
        long amount = t.slots.amount();
        List<Integer> candidates = candidates(purpose);
        int target = t.slots.termHint() != null ? t.slots.termHint() : candidates.get(1);
        Optional<Recommendation> best = firstAvailable(tools.recommend(purpose, amount, target));
        for (int c : candidates) {
            if (best.isPresent()) {
                break;
            }
            best = firstAvailable(tools.recommend(purpose, amount, c));
        }
        if (best.isEmpty()) {
            noProduct(t, purpose, amount, target);
            return;
        }
        ProductFacts.ProductInfo p = product(best.get().code());
        TreeSet<Integer> options = new TreeSet<>();
        for (int c : candidates) {
            if (c >= p.minTerm() && c <= p.maxTerm()) {
                options.add(c);
            }
        }
        if (target >= p.minTerm() && target <= p.maxTerm()) {
            options.add(target);
        }
        if (options.isEmpty()) {
            options.add(Math.max(p.minTerm(), Math.min(p.maxTerm(), target)));
        }
        // Оставляем три срока, ближайших к подсказке клиента
        List<Integer> terms = new ArrayList<>(options);
        terms.sort(Comparator.comparingInt(n -> Math.abs(n - target)));
        terms = new ArrayList<>(terms.subList(0, Math.min(3, terms.size())));
        int recommended = terms.get(0);
        Collections.sort(terms);

        double rate = best.get().indicativeRatePct();
        t.state = DialogState.TERM;
        t.slots = t.slots.withProduct(p.code());
        t.sources.add("product." + p.code());
        if (p.revolving()) {
            t.say(String.format(Locale.ROOT, "Для такой задачи подходит «%s», ставка от %s%% годовых. Это лимит на счёте: "
                    + "вы тратите сверх остатка, а долг гасится деньгами, которые приходят от клиентов.", p.name(), pct(rate)));
            t.button("Подставить " + money(amount) + " на " + months(recommended), Action.FILL_FORM,
                    "purpose", purpose.name(), "amount", String.valueOf(amount), "term", String.valueOf(recommended));
        } else {
            t.say(String.format(Locale.ROOT, "Для такой задачи подходит «%s», ставка от %s%% годовых. Чем короче срок, тем выше "
                    + "платёж и меньше переплата. Ориентировочно за %s:", p.name(), pct(rate), money(amount)));
            List<TermOption> rows = new ArrayList<>();
            for (int n : terms) {
                double payment = PricingCalculator.annuity(amount, rate, n);
                rows.add(new TermOption(n, payment, payment * n - amount, n == recommended));
                t.button("Подставить " + money(amount) + " на " + months(n), Action.FILL_FORM,
                        "purpose", purpose.name(), "amount", String.valueOf(amount), "term", String.valueOf(n));
            }
            t.terms = rows;
        }
        t.button("Изменить сумму", Action.CHANGE_AMOUNT);
        if (available(tools.recommend(purpose, amount, recommended)).size() > 1) {
            t.button("Чем отличаются продукты?", Action.COMPARE, "term", String.valueOf(recommended));
        }
    }

    private void noProduct(Turn t, Purpose purpose, long amount, int term) {
        Set<String> forPurpose = tools.products().stream().filter(p -> p.purposes().contains(purpose))
                .map(ProductFacts.ProductInfo::code).collect(Collectors.toSet());
        List<String> hints = tools.recommend(purpose, amount, term).stream()
                .filter(r -> forPurpose.contains(r.code()))
                .flatMap(r -> r.limits().stream())
                .distinct()
                .toList();
        t.state = DialogState.AMOUNT;
        t.say("Под такие условия продукта нет");
        if (hints.isEmpty()) {
            t.say("Для этой цели у банка нет подходящего продукта, попробуйте выбрать другую цель");
        } else {
            t.say("Что можно изменить:\n" + hints.stream().map(h -> "• " + h).collect(Collectors.joining("\n")));
        }
        t.button("Изменить сумму", Action.CHANGE_AMOUNT);
        t.button("Выбрать другую цель", Action.START, "topic", "purpose");
    }

    private void summary(Turn t, int term) {
        t.slots = t.slots.withTerm(term);
        if (t.slots.purpose() == null || t.slots.amount() == null) {
            askAmount(t, null);
            return;
        }
        t.state = DialogState.SUMMARY;
        t.say("Итого: «" + t.slots.purpose().label() + "», " + money(t.slots.amount()) + " на " + months(term) + ".");
        t.button("Подставить в форму и подобрать продукты", Action.FILL_FORM, "purpose", t.slots.purpose().name(),
                "amount", String.valueOf(t.slots.amount()), "term", String.valueOf(term));
        t.button("Изменить срок", Action.CHANGE_TERM);
        t.button("Изменить сумму", Action.CHANGE_AMOUNT);
    }

    private void fillForm(Turn t, Map<String, String> p) {
        Purpose purpose = p.containsKey("purpose") ? purpose(p.get("purpose")) : t.slots.purpose();
        Long amount = p.containsKey("amount") ? longParam(p, "amount") : t.slots.amount();
        Integer term = p.containsKey("term") ? intParam(p, "term", 1, 120) : t.slots.termMonths();
        if (purpose == null || amount == null || term == null) {
            throw ApiException.badRequest("INCOMPLETE_NEED", "Не хватает цели, суммы или срока");
        }
        t.slots = new Slots(purpose, amount, term, t.slots.termHint(), t.slots.productCode());
        t.ui("FILL_NEED_FORM", "purpose", purpose.name(), "amount", String.valueOf(amount), "term", String.valueOf(term));
        t.ui("HIGHLIGHT", "target", "recommendations");
        int count = available(tools.recommend(purpose, amount, term)).size();
        t.state = DialogState.PRODUCTS;
        t.say("Подставил в форму: «" + purpose.label() + "», " + money(amount) + " на " + months(term) + ".");
        if (count == 0) {
            t.say("Под эти условия продуктов нет, на странице есть подсказка, что изменить");
        } else {
            t.say("Подходящие продукты показаны на странице. Оформить заявку можно кнопкой на карточке продукта.");
        }
        if (count > 1) {
            t.button("Чем отличаются продукты?", Action.COMPARE);
        }
        t.button("Изменить срок", Action.CHANGE_TERM);
        t.button("Изменить сумму", Action.CHANGE_AMOUNT);
    }

    /** Разница подходящих продуктов простыми словами и цифры по задаче клиента. */
    private void compare(Turn t) {
        Slots s = t.slots;
        if (s.purpose() == null || s.amount() == null || s.termMonths() == null && s.termHint() == null) {
            askPurpose(t, "Сначала выясним задачу, тогда сравню подходящие продукты. Для чего нужны деньги?");
            return;
        }
        int term = s.termMonths() != null ? s.termMonths() : s.termHint();
        List<Recommendation> fit = available(tools.recommend(s.purpose(), s.amount(), term));
        if (fit.isEmpty()) {
            noProduct(t, s.purpose(), s.amount(), term);
            return;
        }
        t.state = DialogState.COMPARE;
        if (fit.size() == 1) {
            ProductFacts.ProductInfo p = product(fit.get(0).code());
            t.say("Под ваши условия подходит один продукт: «" + p.name() + "».");
            kb.get("product." + p.code()).ifPresent(t::chunk);
        } else {
            List<String> pair = new ArrayList<>(List.of(fit.get(0).code(), fit.get(1).code()));
            Collections.sort(pair);
            kb.get("compare." + pair.get(0) + "." + pair.get(1)).ifPresent(t::chunk);
            List<String> facts = new ArrayList<>();
            for (Recommendation r : fit) {
                ProductFacts.ProductInfo p = product(r.code());
                String line = "• «" + p.name() + "»: ставка от " + pct(r.indicativeRatePct()) + "% годовых";
                line += p.revolving() ? ", долг гасится поступлениями на счёт"
                        : ", платёж около " + money(PricingCalculator.annuity(s.amount(), r.indicativeRatePct(), term))
                                + " в месяц на " + months(term);
                facts.add(line);
            }
            t.say("Для " + money(s.amount()) + ":\n" + String.join("\n", facts));
        }
        for (Recommendation r : fit) {
            t.button("Выбрать «" + r.name() + "»", Action.SELECT_PRODUCT, "product", r.code());
        }
        t.button("Как выбрать срок?", Action.TERM_HELP);
        if (t.pageKind() == Page.NEED) {
            t.ui("HIGHLIGHT", "target", "recommendations");
        }
    }

    private void selectProduct(Turn t, String code) {
        ProductFacts.ProductInfo p = product(code);
        Slots s = t.slots.withProduct(p.code());
        t.slots = s;
        List<String> params = new ArrayList<>(List.of("product", p.code()));
        if (s.purpose() != null && p.purposes().contains(s.purpose())) {
            params.addAll(List.of("purpose", s.purpose().name()));
        }
        if (s.amount() != null) {
            params.addAll(List.of("amount", String.valueOf(s.amount())));
        }
        Integer term = s.termMonths() != null ? s.termMonths() : s.termHint();
        if (term != null) {
            params.addAll(List.of("term", String.valueOf(term)));
        }
        t.ui("OPEN_WIZARD", params.toArray(String[]::new));
        t.state = DialogState.WIZARD_HELP;
        t.say("Открыл заявку на «" + p.name() + "». Проверьте условия и нажмите «Далее». Черновик сохранится сам, "
                + "а в банк заявка уйдёт только после того, как вы нажмёте «Отправить заявку».");
        stepButtons(t, 0);
    }

    // --- Подсказки в мастере и на странице заявки

    private void wizardHelp(Turn t) {
        int step = t.page != null && t.page.wizardStep() != null ? t.page.wizardStep() : 0;
        t.state = DialogState.WIZARD_HELP;
        t.say(switch (step) {
            case 1 -> "Шаг «Данные»: укажите, сколько компания уже платит в месяц по другим кредитам и лизингу. Если сумма "
                    + "в подсказке банка совпадает с вашей, поле можно оставить пустым.";
            case 2 -> "Шаг «Документы»: приложите выписку по расчётному счёту за последние 12 месяцев. Без документов "
                    + "заявку тоже можно отправить, банк запросит их при необходимости.";
            case 3 -> "Шаг «Проверка и отправка»: проверьте данные и отметьте согласия. Заявка уйдёт в банк только после "
                    + "нажатия «Отправить заявку».";
            default -> "Шаг «Условия»: проверьте продукт, цель, сумму и срок. Границы суммы и срока продукта указаны "
                    + "под полями.";
        });
        stepButtons(t, step);
    }

    private void stepButtons(Turn t, int step) {
        switch (step) {
            case 1 -> {
                t.button("Что такое платежи по другим кредитам?", Action.KB, "id", "glossary.obligations");
                t.button("Что такое залог?", Action.KB, "id", "glossary.collateral");
                t.button("Показать поле", Action.HIGHLIGHT, "target", "wizard-obligations");
            }
            case 2 -> {
                t.button("Какие документы подойдут?", Action.KB, "id", "process.documents");
                t.button("Где взять выписку?", Action.KB, "id", "process.statement");
            }
            case 3 -> {
                t.button("Зачем согласие на бюро?", Action.KB, "id", "faq.bureau-consent");
                t.button("Что будет после отправки?", Action.KB, "id", "process.after-submit");
                t.button("Показать согласия", Action.HIGHLIGHT, "target", "wizard-consents");
            }
            default -> {
                t.button("Как выбрать срок?", Action.TERM_HELP);
                t.button("Что значит ставка «от»?", Action.KB, "id", "glossary.rate-from");
                t.button("Чем отличаются продукты?", Action.COMPARE);
            }
        }
    }

    private void applicationHelp(Turn t) {
        ApplicationStatus status = null;
        if (t.page != null && t.page.applicationStatus() != null) {
            try {
                status = ApplicationStatus.valueOf(t.page.applicationStatus());
            } catch (IllegalArgumentException e) {
                status = null;
            }
        }
        if (status == null) {
            askPurpose(t, null);
            return;
        }
        t.state = DialogState.APPLICATION_HELP;
        NextStep next = ApplicationViews.nextStep(status);
        t.say(next.title() + ".");
        boolean counter = "COUNTER".equals(t.page.offerKind());
        String chunk = switch (status) {
            case DRAFT -> "process.steps";
            case SUBMITTED, SCORING -> "process.after-submit";
            case MANUAL_REVIEW -> "process.waiting";
            case NEED_INFO -> "process.need-info";
            case OFFER_MADE -> counter ? "process.counter-offer" : "process.offer";
            case SIGNING -> "process.signing";
            case ISSUED -> "process.issued";
            case DECLINED -> "process.declined";
            case CLOSED -> "process.closed";
        };
        kb.get(chunk).ifPresent(t::chunk);
        switch (status) {
            case NEED_INFO -> {
                t.button("Открыть форму ответа", Action.HIGHLIGHT, "target", "info-request");
                t.button("Какие документы подойдут?", Action.KB, "id", "process.documents");
            }
            case OFFER_MADE -> {
                t.button("Показать предложение", Action.HIGHLIGHT, "target", "offer");
                if (counter) {
                    t.button("Как читать предложение?", Action.KB, "id", "process.offer");
                } else {
                    t.button("Можно ли погасить досрочно?", Action.KB, "id", "faq.early-repayment");
                }
                t.button("Подать новую заявку", Action.GO_NEED);
            }
            case SIGNING -> {
                t.button("Показать блок подписания", Action.HIGHLIGHT, "target", "signing");
                t.button("Можно ли погасить досрочно?", Action.KB, "id", "faq.early-repayment");
            }
            case DECLINED, CLOSED -> t.button("Подать новую заявку", Action.GO_NEED);
            case SUBMITTED, SCORING, MANUAL_REVIEW -> t.button("Сколько ждать?", Action.KB, "id", "process.waiting");
            case DRAFT -> t.button("Как устроено оформление", Action.KB, "id", "process.steps");
            case ISSUED -> t.button("Подобрать новый кредит", Action.GO_NEED);
        }
    }

    // --- Свободный текст

    /** Ответ на шаге подбора: цель своими словами, сумма или срок. Возвращает false, если это вопрос. */
    private boolean understoodInFlow(Turn t, String text) {
        switch (t.state) {
            case GREETING, PURPOSE -> {
                if (t.state == DialogState.GREETING && isQuestion(text)) {
                    return false;
                }
                List<KnowledgeChunk> hits = kb.search(text, Set.of("purposes"), 1);
                if (hits.isEmpty()) {
                    return false;
                }
                Purpose purpose = Purpose.valueOf(hits.get(0).tag());
                String intro = "Похоже, ваша задача называется «" + purpose.label() + "».";
                Optional<Long> amount = TextParsers.amount(text);
                if (amount.isPresent()) {
                    t.slots = new Slots(purpose, null, null, null, null);
                    amountGiven(t, amount.get(), intro);
                } else {
                    purposeChosen(t, purpose, intro);
                }
                return true;
            }
            case PURPOSE_DETAIL -> {
                Optional<Integer> months = TextParsers.months(text);
                months.ifPresent(m -> {
                    t.slots = t.slots.withTermHint(m);
                    askAmount(t, null);
                });
                return months.isPresent();
            }
            case AMOUNT -> {
                Optional<Long> amount = TextParsers.amount(text);
                amount.ifPresent(a -> amountGiven(t, a, null));
                return amount.isPresent();
            }
            case TERM, SUMMARY, PRODUCTS, COMPARE -> {
                Optional<Integer> months = TextParsers.months(text);
                if (months.isPresent()) {
                    summary(t, months.get());
                    return true;
                }
                Optional<Long> amount = isQuestion(text) ? Optional.empty() : TextParsers.amount(text);
                amount.ifPresent(a -> amountGiven(t, a, null));
                return amount.isPresent();
            }
            default -> {
                return false;
            }
        }
    }

    /** Кнопки возврата к шагу, на котором клиент задал вопрос. */
    private void contextButtons(Turn t) {
        switch (t.state) {
            case PURPOSE_DETAIL -> {
                if (t.slots.purpose() != null) {
                    t.button("Вернуться к подбору", Action.PURPOSE, "purpose", t.slots.purpose().name());
                }
            }
            case AMOUNT -> t.button("Вернуться к сумме", Action.CHANGE_AMOUNT);
            case TERM, SUMMARY -> t.button("Вернуться к выбору срока", Action.CHANGE_TERM);
            case PRODUCTS, COMPARE -> t.button("Чем отличаются продукты?", Action.COMPARE);
            case WIZARD_HELP -> t.button("Подсказки по этому шагу", Action.START, "topic", "wizard");
            case APPLICATION_HELP -> t.button("Что сейчас с заявкой", Action.START, "topic", "application");
            default -> t.button("Подобрать кредит", Action.START, "topic", "purpose");
        }
    }

    private static boolean isQuestion(String text) {
        String t = Guardrails.normalize(text).strip();
        return t.endsWith("?") || t.matches("(?U)^(что|как|зачем|почему|сколько|где|можно|чем|когда|какой|какая|какие|каков|нужен|нужна|нужны|нужно ли)\\b.*");
    }

    // --- Общее

    private Reply finish(Turn t) {
        String text = String.join("\n\n", t.paragraphs);
        List<String> bad = Guardrails.violations(text);
        if (!bad.isEmpty()) {
            // Шаблоны и база знаний проверяются тестами, сюда попадать не должны
            log.warn("assistant reply blocked by output filter: {}", bad);
            text = FALLBACK;
            t.terms = List.of();
        }
        return new Reply(t.state, t.slots, text, t.terms, t.buttons, t.ui, t.sources);
    }

    /** Поля разговора, дополненные значениями формы на странице. С {@code preferPage} форма важнее разговора. */
    private static Slots merge(Slots slots, PageContext page, boolean preferPage) {
        Slots s = slots == null ? Slots.EMPTY : slots;
        if (page == null) {
            return s;
        }
        return new Slots(pick(s.purpose(), page.purpose(), preferPage), pick(s.amount(), page.amount(), preferPage),
                pick(s.termMonths(), page.termMonths(), preferPage), s.termHint(),
                pick(s.productCode(), page.productCode(), preferPage));
    }

    private static <T> T pick(T own, T fromPage, boolean preferPage) {
        if (preferPage) {
            return fromPage != null ? fromPage : own;
        }
        return own != null ? own : fromPage;
    }

    private ProductFacts.ProductInfo product(String code) {
        return tools.products().stream().filter(p -> p.code().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PRODUCT", "Продукт не найден"));
    }

    private static List<Recommendation> available(List<Recommendation> recs) {
        return recs.stream().filter(Recommendation::available).toList();
    }

    private static Optional<Recommendation> firstAvailable(List<Recommendation> recs) {
        return recs.stream().filter(Recommendation::available).findFirst();
    }

    private static boolean longTerm(Purpose p) {
        return p == Purpose.EQUIPMENT || p == Purpose.TRANSPORT || p == Purpose.NEW_DIRECTION;
    }

    private static List<Integer> candidates(Purpose p) {
        return switch (p) {
            case CASH_GAP -> List.of(3, 6, 12);
            case PURCHASE, WORKING_CAPITAL -> List.of(6, 9, 12);
            case EQUIPMENT, TRANSPORT, NEW_DIRECTION -> List.of(24, 36, 60);
        };
    }

    private static Purpose purpose(String value) {
        try {
            return Purpose.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("UNKNOWN_PURPOSE", "Неизвестная цель");
        }
    }

    private static long longParam(Map<String, String> p, String key) {
        try {
            long v = Long.parseLong(p.get(key));
            if (v <= 0 || v > 1_000_000_000L) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("BAD_PARAM", "Неверное значение " + key);
        }
    }

    private static int intParam(Map<String, String> p, String key, int min, int max) {
        try {
            int v = Integer.parseInt(p.get(key));
            if (v < min || v > max) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("BAD_PARAM", "Неверное значение " + key);
        }
    }

    private static Map<String, String> params(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
