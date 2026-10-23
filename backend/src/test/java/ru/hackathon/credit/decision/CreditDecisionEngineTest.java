package ru.hackathon.credit.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ru.hackathon.credit.decision.DecisionResult.Outcome;
import ru.hackathon.credit.pricing.PricingCalculator;
import ru.hackathon.credit.scoring.Factor;
import ru.hackathon.credit.scoring.Grade;
import ru.hackathon.credit.scoring.ScoringOutcome;

/** Порядок правил кредитной логики (бриф, раздел 7). */
class CreditDecisionEngineTest {

    private final CreditDecisionEngine engine = new CreditDecisionEngine(new PricingCalculator(15.0));

    @Test
    void hardStopDeclines() {
        DecisionResult d = engine.decide(input(outcome(95, true), false, 1_500_000.0, 500_000, 4_000_000, 0, List.of()));
        assertEquals(Outcome.DECLINE, d.outcome());
        assertEquals("HARD_STOP", d.reasonCode());
    }

    @Test
    void gradeDDeclinesWithReasons() {
        DecisionResult d = engine.decide(input(outcome(30, false), false, 1_500_000.0, 500_000, 4_000_000, 0, List.of()));
        assertEquals(Outcome.DECLINE, d.outcome());
        assertTrue(!d.reasons().isEmpty());
    }

    @Test
    void unavailableSourceGoesToManualReview() {
        DecisionResult d = engine.decide(input(outcome(90, false), false, 1_500_000.0, 500_000, 4_000_000, 0,
                List.of("бюро кредитных историй")));
        assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
        assertEquals("SOURCE_UNAVAILABLE", d.reasonCode());
    }

    @Test
    void alwaysManualProductAndGradeCAndAutoLimit() {
        assertEquals("PRODUCT_MANUAL", engine.decide(input(outcome(90, false), true, null, 500_000, 4_000_000, 0,
                List.of())).reasonCode());
        assertEquals("GRADE_C", engine.decide(input(outcome(60, false), false, 1_500_000.0, 500_000, 4_000_000, 0,
                List.of())).reasonCode());
        assertEquals("ABOVE_AUTO_LIMIT", engine.decide(input(outcome(90, false), false, 1_500_000.0, 1_600_000,
                4_000_000, 0, List.of())).reasonCode());
    }

    @Test
    void lowLoadApprovesOnRequestedTerms() {
        DecisionResult d = engine.decide(input(outcome(90, false), false, 1_500_000.0, 1_200_000, 4_000_000, 120_000,
                List.of()));
        assertEquals(Outcome.AUTO_APPROVE, d.outcome());
        assertEquals(1_200_000.0, d.offerAmount().doubleValue());
    }

    @Test
    void highLoadGivesCounterOfferWithSmallerAmount() {
        DecisionResult d = engine.decide(input(outcome(72, false), false, 1_500_000.0, 1_400_000, 800_000, 90_000,
                List.of()));
        assertEquals(Outcome.AUTO_COUNTER, d.outcome());
        assertEquals(840_000.0, d.offerAmount().doubleValue());
        assertEquals(1, d.changes().size());
    }

    @Test
    void tooHighLoadGoesToManualReview() {
        DecisionResult d = engine.decide(input(outcome(72, false), false, 1_500_000.0, 1_400_000, 500_000, 120_000,
                List.of()));
        assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
        assertEquals("LOAD_TOO_HIGH", d.reasonCode());
    }

    private static ScoringOutcome outcome(int score, boolean hardStop) {
        return new ScoringOutcome(score, Grade.fromScore(score), hardStop, hardStop ? "статус компании" : null, 0.1,
                List.of(new Factor("TAX_DEBT", "Налоговая задолженность", "есть", -12, "Есть задолженность"),
                        new Factor("BUSINESS_AGE", "Возраст бизнеса", "40 мес.", 15, "Больше трёх лет")));
    }

    private static DecisionInput input(ScoringOutcome s, boolean alwaysManual, Double autoLimit, double amount,
                                       double avgMonthly, double obligations, List<String> unavailable) {
        return new DecisionInput(s, "Тестовый продукт", 3.0, alwaysManual, autoLimit, 100_000, amount, 6, obligations,
                avgMonthly, unavailable, null);
    }
}
