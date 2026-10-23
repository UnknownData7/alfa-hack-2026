package ru.hackathon.credit.integration.mock;

import ru.hackathon.credit.integration.CreditBureauClient;

public class MockCreditBureauClient implements CreditBureauClient {

    private final MockExternalData data;

    public MockCreditBureauClient(MockExternalData data) {
        this.data = data;
    }

    @Override
    public BureauReport report(String inn) {
        MockExternalData.Profile p = data.profile("BUREAU", inn);
        String summary = p.maxDelinquencyDays() == 0
                ? "Просрочек нет"
                : "Максимальная просрочка " + p.maxDelinquencyDays() + " дн. за 24 месяца";
        return new BureauReport(inn, p.maxDelinquencyDays(), p.activeLoans(), summary);
    }
}
