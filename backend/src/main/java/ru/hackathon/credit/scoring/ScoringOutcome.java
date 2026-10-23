package ru.hackathon.credit.scoring;

import java.util.Comparator;
import java.util.List;

/**
 * Результат скоринга: итоговый балл, грейд, жёсткий стоп и разбор по факторам.
 */
public record ScoringOutcome(
        int totalScore,
        Grade grade,
        boolean hardStop,
        String hardStopReason,
        double paymentLoad,
        List<Factor> factors) {

    /** Факторы, снизившие оценку, от самого сильного. */
    public List<Factor> negativeFactors() {
        return factors.stream()
                .filter(f -> f.points() < 0)
                .sorted(Comparator.comparingInt(Factor::points))
                .toList();
    }

    public List<Factor> positiveFactors() {
        return factors.stream()
                .filter(f -> f.points() > 0)
                .sorted(Comparator.comparingInt(Factor::points).reversed())
                .toList();
    }
}
