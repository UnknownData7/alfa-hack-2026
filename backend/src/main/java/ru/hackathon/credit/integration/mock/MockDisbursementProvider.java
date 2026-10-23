package ru.hackathon.credit.integration.mock;

import java.math.BigDecimal;
import java.util.UUID;

import ru.hackathon.credit.integration.DisbursementProvider;

/** Симуляция зачисления: денежных операций нет, возвращается только номер операции. */
public class MockDisbursementProvider implements DisbursementProvider {

    @Override
    public DisbursementReceipt disburse(String applicationNumber, BigDecimal amount, String accountMasked) {
        return new DisbursementReceipt("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), true);
    }
}
