package ru.hackathon.credit.decision;

import java.util.List;

/**
 * Исход кредитной логики с объяснением: код причины, краткий вывод, причины и изменения условий.
 */
public record DecisionResult(
        Outcome outcome,
        String reasonCode,
        String summary,
        List<String> reasons,
        List<String> changes,
        Double offerAmount,
        Integer offerTermMonths) {

    public enum Outcome {
        AUTO_APPROVE, AUTO_COUNTER, DECLINE, MANUAL_REVIEW
    }

    static DecisionResult approve(String code, String summary, List<String> reasons, double amount, int term) {
        return new DecisionResult(Outcome.AUTO_APPROVE, code, summary, reasons, List.of(), amount, term);
    }

    static DecisionResult counter(String code, String summary, List<String> reasons, List<String> changes,
                                  double amount, int term) {
        return new DecisionResult(Outcome.AUTO_COUNTER, code, summary, reasons, changes, amount, term);
    }

    static DecisionResult decline(String code, String summary, List<String> reasons) {
        return new DecisionResult(Outcome.DECLINE, code, summary, reasons, List.of(), null, null);
    }

    static DecisionResult manual(String code, String summary, List<String> reasons) {
        return new DecisionResult(Outcome.MANUAL_REVIEW, code, summary, reasons, List.of(), null, null);
    }
}
