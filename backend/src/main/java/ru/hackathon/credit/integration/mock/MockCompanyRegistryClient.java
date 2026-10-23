package ru.hackathon.credit.integration.mock;

import ru.hackathon.credit.integration.CompanyRegistryClient;

public class MockCompanyRegistryClient implements CompanyRegistryClient {

    private final MockExternalData data;

    public MockCompanyRegistryClient(MockExternalData data) {
        this.data = data;
    }

    @Override
    public RegistryInfo lookup(String inn) {
        String status = data.profile("REGISTRY", inn).registryStatus();
        return switch (status) {
            case "LIQUIDATING" -> new RegistryInfo(inn, status, "в процессе ликвидации", false);
            default -> new RegistryInfo(inn, status, "действующая", true);
        };
    }
}
