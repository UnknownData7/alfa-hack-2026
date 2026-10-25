package ru.hackathon.credit.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ru.hackathon.credit.scoring.Grade;

class PricingCalculatorTest {

    private final PricingCalculator pricing = new PricingCalculator(15.0);

    @Test
    void rateIsKeyPlusSpreadPlusRiskPremium() {
        assertEquals(19.0, pricing.rate(3.0, Grade.A));
        assertEquals(20.5, pricing.rate(3.0, Grade.B));
        assertEquals(22.5, pricing.rate(3.0, Grade.C));
    }

    @Test
    void annuityMatchesFormulaAndIsRoundedToRuble() {
        // P = S * r / (1 - (1 + r)^-n), r = 19% / 12
        assertEquals(144110.0, PricingCalculator.annuity(1_200_000, 19.0, 9));
        assertEquals(158421.0, PricingCalculator.annuity(900_000, 19.0, 6));
        assertEquals(10000.0, PricingCalculator.annuity(120_000, 0, 12));
    }

    @Test
    void maxAmountForPaymentIsRoundedDownToTenThousand() {
        double max = PricingCalculator.maxAmountForPayment(150_000, 20.5, 6);
        assertEquals(840_000.0, max);
        assertEquals(0.0, PricingCalculator.maxAmountForPayment(-1, 20.5, 6));
    }

    @Test
    void quoteContainsOverpayment() {
        PricingCalculator.Quote q = pricing.quote(1_200_000, 9, 3.0, Grade.A);
        assertEquals(0, q.monthlyPayment().compareTo(java.math.BigDecimal.valueOf(144110)));
        assertEquals(0, q.overpayment().compareTo(java.math.BigDecimal.valueOf(144110L * 9 - 1_200_000)));
    }
}
