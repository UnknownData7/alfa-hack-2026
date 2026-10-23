package ru.hackathon.credit.scoring;

/**
 * Входные данные скоринга. Собираются из профиля компании, заявки и ответов внешних источников.
 *
 * @param estimatedMonthlyPayment ориентировочный платёж по запрашиваемому кредиту
 * @param maxDelinquencyDays      максимальная просрочка по данным БКИ, дней (0 — просрочек не было)
 */
public record ScoringInput(
        int businessAgeMonths,
        double requestedAmount,
        double annualTurnover,
        double avgMonthlyTurnover,
        double turnoverGrowthPct,
        double monthlyObligations,
        double estimatedMonthlyPayment,
        int maxDelinquencyDays,
        boolean bureauAvailable,
        boolean hasTaxDebt,
        String industryRisk,
        boolean companyActive,
        String companyStatusText) {
}
