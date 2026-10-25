package ru.hackathon.credit.integration;

/** Бюро кредитных историй: платёжная дисциплина компании. */
public interface CreditBureauClient {

    BureauReport report(String inn);

    /**
     * @param maxDelinquencyDays максимальная просрочка за 24 месяца, дней (0: просрочек не было)
     */
    record BureauReport(String inn, int maxDelinquencyDays, int activeLoans, String summary) {
    }
}
