package ru.hackathon.credit.decision;

import java.util.List;

import ru.hackathon.credit.scoring.ScoringOutcome;

/**
 * Всё, что нужно кредитной логике: оценка риска, параметры продукта и заявки.
 *
 * @param forceManualReason если задан, заявка уходит на ручную проверку (например, возврат после запроса данных)
 */
public record DecisionInput(
        ScoringOutcome scoring,
        String productName,
        double spreadPct,
        boolean alwaysManual,
        Double autoLimitAmount,
        double productMinAmount,
        double requestedAmount,
        int requestedTermMonths,
        double monthlyObligations,
        double avgMonthlyTurnover,
        List<String> unavailableSources,
        String forceManualReason) {
}
