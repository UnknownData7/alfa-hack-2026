package ru.hackathon.credit.task;

import java.util.ArrayList;
import java.util.List;

/**
 * Правила очереди андеррайтера: приоритет задачи и набор проверок по параметрам заявки (бриф, раздел 8).
 */
public final class TaskRules {

    public static final int REPEAT_PRIORITY = 1;

    private TaskRules() {
    }

    /** Приоритет 1–10, где 1 самый высокий. */
    public static int priority(double amount, boolean repeat) {
        if (repeat) {
            return REPEAT_PRIORITY;
        }
        if (amount >= 5_000_000) {
            return 2;
        }
        if (amount < 500_000) {
            return 7;
        }
        return 5;
    }

    public record CheckDefinition(String code, String title, String goal) {
    }

    public record CheckContext(boolean requiresCollateral, double turnoverCoverage, boolean hasDocuments,
                               int maxDelinquencyDays, int businessAgeMonths, String industryRisk) {
    }

    public static List<CheckDefinition> checks(CheckContext c) {
        List<CheckDefinition> list = new ArrayList<>();
        if (c.turnoverCoverage() > 0.30 || !c.hasDocuments()) {
            list.add(new CheckDefinition("TURNOVER_CONFIRMATION", "Подтверждение оборотов",
                    "Сверить заявленный оборот с выпиской по счёту и документами клиента"));
        }
        if (c.requiresCollateral()) {
            list.add(new CheckDefinition("COLLATERAL", "Проверка залога",
                    "Оценить описание залога, его ликвидность и достаточность для суммы кредита"));
        }
        if (c.maxDelinquencyDays() > 0) {
            list.add(new CheckDefinition("CREDIT_HISTORY", "Кредитная история",
                    "Оценить причины просрочек и текущее исполнение обязательств"));
        }
        if (c.businessAgeMonths() < 12 || "HIGH".equals(c.industryRisk())) {
            list.add(new CheckDefinition("BUSINESS_ACTIVITY", "Проверка деятельности",
                    "Убедиться, что бизнес ведёт реальную деятельность: молодой бизнес или отрасль с повышенным риском"));
        }
        if (list.isEmpty()) {
            list.add(new CheckDefinition("GENERAL_REVIEW", "Общая оценка заявки",
                    "Проверить параметры заявки и принять решение по результатам скоринга"));
        }
        return list;
    }
}
