package ru.hackathon.credit.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Границы порогов каждого фактора (docs/CREDIT_RULES.md). */
class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();

    @Test
    void businessAgeThresholds() {
        assertEquals(15, ScoringEngine.businessAge(36).points());
        assertEquals(8, ScoringEngine.businessAge(35).points());
        assertEquals(8, ScoringEngine.businessAge(12).points());
        assertEquals(0, ScoringEngine.businessAge(11).points());
        assertEquals(0, ScoringEngine.businessAge(6).points());
        assertEquals(-10, ScoringEngine.businessAge(5).points());
    }

    @Test
    void coverageThresholds() {
        assertEquals(20, ScoringEngine.coverage(0.10).points());
        assertEquals(10, ScoringEngine.coverage(0.1001).points());
        assertEquals(10, ScoringEngine.coverage(0.25).points());
        assertEquals(0, ScoringEngine.coverage(0.50).points());
        assertEquals(-15, ScoringEngine.coverage(0.5001).points());
    }

    @Test
    void paymentLoadThresholds() {
        assertEquals(15, ScoringEngine.load(0.15).points());
        assertEquals(5, ScoringEngine.load(0.30).points());
        assertEquals(-5, ScoringEngine.load(0.45).points());
        assertEquals(-20, ScoringEngine.load(0.4501).points());
    }

    @Test
    void creditHistoryThresholds() {
        assertEquals(15, ScoringEngine.creditHistory(input(0, true)).points());
        assertEquals(3, ScoringEngine.creditHistory(input(29, true)).points());
        assertEquals(-20, ScoringEngine.creditHistory(input(30, true)).points());
        assertEquals(-20, ScoringEngine.creditHistory(input(90, true)).points());
        assertEquals(0, ScoringEngine.creditHistory(input(0, false)).points());
    }

    @Test
    void dynamicsIndustryAndTax() {
        assertEquals(10, ScoringEngine.dynamics(10).points());
        assertEquals(4, ScoringEngine.dynamics(9.9).points());
        assertEquals(4, ScoringEngine.dynamics(-10).points());
        assertEquals(-10, ScoringEngine.dynamics(-10.1).points());
        assertEquals(5, ScoringEngine.industry("LOW").points());
        assertEquals(0, ScoringEngine.industry("MEDIUM").points());
        assertEquals(-8, ScoringEngine.industry("HIGH").points());
        assertEquals(-12, ScoringEngine.tax(true).points());
        assertEquals(0, ScoringEngine.tax(false).points());
    }

    @Test
    void gradeBoundaries() {
        assertEquals(Grade.A, Grade.fromScore(80));
        assertEquals(Grade.B, Grade.fromScore(79));
        assertEquals(Grade.B, Grade.fromScore(65));
        assertEquals(Grade.C, Grade.fromScore(64));
        assertEquals(Grade.C, Grade.fromScore(50));
        assertEquals(Grade.D, Grade.fromScore(49));
    }

    @Test
    void scoreIsClampedAndHasSevenFactors() {
        ScoringOutcome best = engine.score(new ScoringInput(62, 1_000_000, 48_000_000, 4_000_000, 14, 0, 50_000,
                0, true, false, "LOW", true, "действующая"));
        assertEquals(100, best.totalScore());
        assertEquals(7, best.factors().size());
        assertFalse(best.hardStop());
    }

    @Test
    void hardStops() {
        ScoringOutcome liquidating = engine.score(new ScoringInput(70, 500_000, 6_000_000, 500_000, -2, 50_000,
                90_000, 0, true, false, "MEDIUM", false, "в процессе ликвидации"));
        assertTrue(liquidating.hardStop());
        ScoringOutcome overdue = engine.score(input(91, true));
        assertTrue(overdue.hardStop());
        ScoringOutcome ninety = engine.score(input(90, true));
        assertFalse(ninety.hardStop());
    }

    private static ScoringInput input(int delinquencyDays, boolean bureauAvailable) {
        return new ScoringInput(40, 1_000_000, 20_000_000, 1_666_667, 0, 100_000, 100_000, delinquencyDays,
                bureauAvailable, false, "MEDIUM", true, "действующая");
    }
}
