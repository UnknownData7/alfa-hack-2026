package ru.hackathon.credit.decision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.hackathon.credit.pricing.PricingCalculator;
import ru.hackathon.credit.scoring.Factor;
import ru.hackathon.credit.scoring.Grade;
import ru.hackathon.credit.scoring.ScoringOutcome;

/**
 * Кредитная логика поверх скоринга (бриф, раздел 7). Правила применяются по порядку, первое сработавшее определяет исход.
 * Одна и та же оценка риска даёт разные решения в зависимости от продукта, суммы и платёжной нагрузки.
 */
public class CreditDecisionEngine {

    public static final double MAX_AUTO_LOAD = 0.30;
    public static final double MIN_COUNTER_SHARE = 0.5;

    private static final Locale RU = Locale.forLanguageTag("ru");

    private final PricingCalculator pricing;

    public CreditDecisionEngine(PricingCalculator pricing) {
        this.pricing = pricing;
    }

    public DecisionResult decide(DecisionInput in) {
        ScoringOutcome s = in.scoring();

        if (s.hardStop()) {
            return DecisionResult.decline("HARD_STOP", "Отказ: " + s.hardStopReason(), List.of(s.hardStopReason()));
        }
        if (!in.unavailableSources().isEmpty()) {
            return DecisionResult.manual("SOURCE_UNAVAILABLE",
                    "Не все внешние источники ответили, заявку проверит специалист банка",
                    List.of("Не получены данные: " + String.join(", ", in.unavailableSources())));
        }
        if (s.grade() == Grade.D) {
            List<String> reasons = s.negativeFactors().stream().limit(4).map(CreditDecisionEngine::describe).toList();
            return DecisionResult.decline("GRADE_D", "Отказ: высокая оценка риска", reasons);
        }
        if (in.forceManualReason() != null) {
            return DecisionResult.manual("REPEAT_REVIEW", in.forceManualReason(), List.of(in.forceManualReason()));
        }
        if (in.alwaysManual()) {
            return DecisionResult.manual("PRODUCT_MANUAL",
                    "Продукт рассматривается специалистом банка: нужна оценка залога и проекта",
                    List.of("Для продукта «" + in.productName() + "» решение принимает специалист банка"));
        }
        if (s.grade() == Grade.C) {
            return DecisionResult.manual("GRADE_C", "Средняя оценка риска, заявку проверит специалист банка",
                    s.negativeFactors().stream().limit(3).map(CreditDecisionEngine::describe).toList());
        }
        if (in.autoLimitAmount() != null && in.requestedAmount() > in.autoLimitAmount()) {
            return DecisionResult.manual("ABOVE_AUTO_LIMIT", "Сумма выше лимита автоматического решения",
                    List.of(String.format(RU, "Автоматически решаются заявки до %,.0f ₽", in.autoLimitAmount())));
        }

        double rate = pricing.rate(in.spreadPct(), s.grade());
        double payment = PricingCalculator.annuity(in.requestedAmount(), rate, in.requestedTermMonths());
        double load = (in.monthlyObligations() + payment) / in.avgMonthlyTurnover();
        if (load <= MAX_AUTO_LOAD) {
            List<String> reasons = new ArrayList<>();
            reasons.add("Оценка риска: грейд " + s.grade());
            s.positiveFactors().stream().limit(3).map(CreditDecisionEngine::describe).forEach(reasons::add);
            return DecisionResult.approve("AUTO_APPROVE", "Одобрено автоматически на запрошенных условиях", reasons,
                    in.requestedAmount(), in.requestedTermMonths());
        }

        double freePayment = MAX_AUTO_LOAD * in.avgMonthlyTurnover() - in.monthlyObligations();
        double maxAmount = Math.min(in.requestedAmount(),
                PricingCalculator.maxAmountForPayment(freePayment, rate, in.requestedTermMonths()));
        if (maxAmount >= in.requestedAmount() * MIN_COUNTER_SHARE && maxAmount >= in.productMinAmount()) {
            String change = String.format(RU, "Сумма снижена с %,.0f до %,.0f ₽, чтобы платежи по кредитам не превышали 30%% "
                    + "среднего месячного оборота", in.requestedAmount(), maxAmount);
            return DecisionResult.counter("AUTO_COUNTER", "Банк готов выдать кредит на меньшую сумму",
                    List.of(String.format(RU, "Платёжная нагрузка на запрошенных условиях %.2f при допустимых 0,30", load),
                            "Оценка риска: грейд " + s.grade()),
                    List.of(change), maxAmount, in.requestedTermMonths());
        }
        return DecisionResult.manual("LOAD_TOO_HIGH", "Высокая платёжная нагрузка, заявку проверит специалист банка",
                List.of(String.format(RU, "Платёжная нагрузка %.2f при допустимых 0,30", load)));
    }

    static String describe(Factor f) {
        return f.title() + ": " + f.value() + " (" + f.comment().toLowerCase(RU) + ")";
    }
}
