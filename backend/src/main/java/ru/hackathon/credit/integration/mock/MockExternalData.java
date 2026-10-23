package ru.hackathon.credit.integration.mock;

import java.util.Map;
import java.util.Set;

/**
 * Детерминированные ответы внешних источников по ИНН. Для неизвестного ИНН источник отвечает «всё в порядке».
 * Все ИНН вымышленные и совпадают с демо-компаниями из сида.
 */
public class MockExternalData {

    record Profile(String registryStatus, int maxDelinquencyDays, int activeLoans, double taxDebt) {
    }

    private static final Profile CLEAN = new Profile("ACTIVE", 0, 1, 0);

    private static final Map<String, Profile> PROFILES = Map.of(
            "7701000005", new Profile("ACTIVE", 45, 3, 180_000),
            "7701000006", new Profile("LIQUIDATING", 0, 0, 0),
            "770100000004", new Profile("ACTIVE", 0, 0, 0),
            "770100000008", new Profile("ACTIVE", 20, 1, 25_000),
            "7701000009", new Profile("ACTIVE", 60, 2, 0));

    private final long latencyMs;
    private final Set<String> unavailableSources;

    public MockExternalData(long latencyMs, Set<String> unavailableSources) {
        this.latencyMs = latencyMs;
        this.unavailableSources = unavailableSources;
    }

    Profile profile(String source, String inn) {
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (unavailableSources.contains(source)) {
            throw new ru.hackathon.credit.integration.ExternalSourceUnavailableException(source,
                    "Источник " + source + " недоступен (имитация сбоя)");
        }
        return PROFILES.getOrDefault(inn, CLEAN);
    }
}
