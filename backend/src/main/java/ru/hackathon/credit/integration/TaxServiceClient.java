package ru.hackathon.credit.integration;

/** Сведения ФНС о налоговой задолженности. */
public interface TaxServiceClient {

    TaxInfo check(String inn);

    record TaxInfo(String inn, boolean hasDebt, double debtAmount) {
    }
}
