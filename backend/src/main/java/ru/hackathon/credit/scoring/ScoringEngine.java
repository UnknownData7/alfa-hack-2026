package ru.hackathon.credit.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Скоринг на прозрачных правилах (бриф, раздел 7). База 40 баллов, итог ограничен диапазоном 0–100.
 * Скоринг только оценивает риск; решение по заявке принимает {@code CreditDecisionEngine}.
 */
public class ScoringEngine {

    public static final int BASE_SCORE = 40;

    private static final Locale RU = Locale.forLanguageTag("ru");

    public ScoringOutcome score(ScoringInput in) {
        List<Factor> factors = new ArrayList<>();
        factors.add(businessAge(in.businessAgeMonths()));

        double coverage = in.annualTurnover() > 0 ? in.requestedAmount() / in.annualTurnover() : Double.MAX_VALUE;
        factors.add(coverage(coverage));

        double load = paymentLoad(in);
        factors.add(load(load));

        factors.add(creditHistory(in));
        factors.add(dynamics(in.turnoverGrowthPct()));
        factors.add(industry(in.industryRisk()));
        factors.add(tax(in.hasTaxDebt()));

        int total = BASE_SCORE + factors.stream().mapToInt(Factor::points).sum();
        total = Math.max(0, Math.min(100, total));

        String hardStopReason = null;
        if (!in.companyActive()) {
            hardStopReason = "Статус компании в реестре: " + in.companyStatusText();
        } else if (in.bureauAvailable() && in.maxDelinquencyDays() > 90) {
            hardStopReason = "Просрочка по кредитам более 90 дней";
        }

        return new ScoringOutcome(total, Grade.fromScore(total), hardStopReason != null, hardStopReason,
                round3(load), List.copyOf(factors));
    }

    /** Платёжная нагрузка: (текущие платежи + ориентировочный платёж по заявке) / средний месячный оборот. */
    public static double paymentLoad(ScoringInput in) {
        if (in.avgMonthlyTurnover() <= 0) {
            return Double.MAX_VALUE;
        }
        return (in.monthlyObligations() + in.estimatedMonthlyPayment()) / in.avgMonthlyTurnover();
    }

    static Factor businessAge(int months) {
        int points;
        String comment;
        if (months >= 36) {
            points = 15;
            comment = "Бизнес работает больше трёх лет";
        } else if (months >= 12) {
            points = 8;
            comment = "Бизнес работает от года до трёх лет";
        } else if (months >= 6) {
            points = 0;
            comment = "Бизнес работает меньше года";
        } else {
            points = -10;
            comment = "Бизнес работает меньше полугода";
        }
        return new Factor("BUSINESS_AGE", "Возраст бизнеса", months + " мес.", points, comment);
    }

    static Factor coverage(double ratio) {
        int points;
        String comment;
        if (ratio <= 0.10) {
            points = 20;
            comment = "Сумма мала относительно годового оборота";
        } else if (ratio <= 0.25) {
            points = 10;
            comment = "Сумма умеренная относительно годового оборота";
        } else if (ratio <= 0.50) {
            points = 0;
            comment = "Сумма заметная относительно годового оборота";
        } else {
            points = -15;
            comment = "Сумма больше половины годового оборота";
        }
        return new Factor("TURNOVER_COVERAGE", "Сумма к годовому обороту", fmt(ratio), points, comment);
    }

    static Factor load(double load) {
        int points;
        String comment;
        if (load <= 0.15) {
            points = 15;
            comment = "Платежи по кредитам занимают малую долю оборота";
        } else if (load <= 0.30) {
            points = 5;
            comment = "Платёжная нагрузка умеренная";
        } else if (load <= 0.45) {
            points = -5;
            comment = "Платежи по кредитам занимают заметную долю оборота";
        } else {
            points = -20;
            comment = "Платежи по кредитам занимают почти половину оборота или больше";
        }
        return new Factor("PAYMENT_LOAD", "Платёжная нагрузка", fmt(load), points, comment);
    }

    static Factor creditHistory(ScoringInput in) {
        if (!in.bureauAvailable()) {
            return new Factor("CREDIT_HISTORY", "Кредитная история", "нет данных", 0,
                    "Бюро кредитных историй не ответило, нужна проверка специалиста");
        }
        int days = in.maxDelinquencyDays();
        if (days == 0) {
            return new Factor("CREDIT_HISTORY", "Кредитная история", "без просрочек", 15,
                    "Обязательства исполнялись вовремя");
        }
        if (days < 30) {
            return new Factor("CREDIT_HISTORY", "Кредитная история", "просрочка до 30 дней", 3,
                    "Были только короткие просрочки");
        }
        if (days <= 90) {
            return new Factor("CREDIT_HISTORY", "Кредитная история", "просрочка " + days + " дн.", -20,
                    "Была просрочка от 30 до 90 дней");
        }
        return new Factor("CREDIT_HISTORY", "Кредитная история", "просрочка " + days + " дн.", -20,
                "Просрочка больше 90 дней: жёсткий стоп");
    }

    static Factor dynamics(double growthPct) {
        String value = (growthPct > 0 ? "+" : "") + String.format(RU, "%.0f%%", growthPct);
        if (growthPct >= 10) {
            return new Factor("TURNOVER_DYNAMICS", "Динамика оборота", value, 10, "Оборот растёт");
        }
        if (growthPct >= -10) {
            return new Factor("TURNOVER_DYNAMICS", "Динамика оборота", value, 4, "Оборот стабилен");
        }
        return new Factor("TURNOVER_DYNAMICS", "Динамика оборота", value, -10, "Оборот снижается");
    }

    static Factor industry(String risk) {
        return switch (risk) {
            case "LOW" -> new Factor("INDUSTRY_RISK", "Риск отрасли", "низкий", 5, "Отрасль с низким риском");
            case "HIGH" -> new Factor("INDUSTRY_RISK", "Риск отрасли", "высокий", -8,
                    "Отрасль с повышенным риском: выручка сильнее зависит от внешних условий");
            default -> new Factor("INDUSTRY_RISK", "Риск отрасли", "средний", 0, "Отрасль со средним риском");
        };
    }

    static Factor tax(boolean hasDebt) {
        return hasDebt
                ? new Factor("TAX_DEBT", "Налоговая задолженность", "есть", -12, "По данным ФНС есть задолженность")
                : new Factor("TAX_DEBT", "Налоговая задолженность", "нет", 0, "Задолженности нет");
    }

    static String fmt(double v) {
        if (v == Double.MAX_VALUE) {
            return "нет оборота";
        }
        return String.format(RU, "%.2f", v);
    }

    static double round3(double v) {
        if (v == Double.MAX_VALUE) {
            return 9.999;
        }
        return Math.round(v * 1000.0) / 1000.0;
    }
}
