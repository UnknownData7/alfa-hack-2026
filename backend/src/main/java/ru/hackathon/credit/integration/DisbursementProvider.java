package ru.hackathon.credit.integration;

import java.math.BigDecimal;

/** Выдача средств на расчётный счёт клиента. */
public interface DisbursementProvider {

    DisbursementReceipt disburse(String applicationNumber, BigDecimal amount, String accountMasked);

    record DisbursementReceipt(String reference, boolean simulated) {
    }
}
