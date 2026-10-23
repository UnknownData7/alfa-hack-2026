package ru.hackathon.credit.integration.mock;

import ru.hackathon.credit.integration.TaxServiceClient;

public class MockTaxServiceClient implements TaxServiceClient {

    private final MockExternalData data;

    public MockTaxServiceClient(MockExternalData data) {
        this.data = data;
    }

    @Override
    public TaxInfo check(String inn) {
        double debt = data.profile("TAX", inn).taxDebt();
        return new TaxInfo(inn, debt > 0, debt);
    }
}
