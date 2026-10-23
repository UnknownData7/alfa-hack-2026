package ru.hackathon.credit.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import ru.hackathon.credit.scoring.Grade;

/**
 * Цена кредита: ставка = ключевая + спред продукта + премия за риск; платёж аннуитетный, округление до рубля.
 */
public class PricingCalculator {

    /** Премия, по которой считается ориентировочный платёж до того, как известен грейд. */
    public static final double ESTIMATE_RISK_PREMIUM = 2.5;

    private final double keyRatePct;

    public PricingCalculator(double keyRatePct) {
        this.keyRatePct = keyRatePct;
    }

    public double keyRatePct() {
        return keyRatePct;
    }

    public static double riskPremium(Grade grade) {
        return switch (grade) {
            case A -> 1.0;
            case B -> 2.5;
            case C -> 4.5;
            // Грейд D не кредитуется автоматически; премия нужна, только если андеррайтер одобрит вручную
            case D -> 6.5;
        };
    }

    public double rate(double spreadPct, Grade grade) {
        return round2(keyRatePct + spreadPct + riskPremium(grade));
    }

    public double estimateRate(double spreadPct) {
        return round2(keyRatePct + spreadPct + ESTIMATE_RISK_PREMIUM);
    }

    /** Аннуитетный платёж P = S * r / (1 - (1 + r)^-n), r = ставка / 12 / 100. */
    public static double annuity(double amount, double ratePct, int months) {
        double r = ratePct / 12.0 / 100.0;
        if (r == 0) {
            return Math.round(amount / months);
        }
        return Math.round(amount * r / (1 - Math.pow(1 + r, -months)));
    }

    /** Максимальная сумма, при которой платёж не превышает заданного. Округление вниз до 10 тыс. ₽. */
    public static double maxAmountForPayment(double payment, double ratePct, int months) {
        if (payment <= 0) {
            return 0;
        }
        double r = ratePct / 12.0 / 100.0;
        double amount = r == 0 ? payment * months : payment * (1 - Math.pow(1 + r, -months)) / r;
        return Math.floor(amount / 10_000.0) * 10_000.0;
    }

    public Quote quote(double amount, int months, double spreadPct, Grade grade) {
        double rate = rate(spreadPct, grade);
        double payment = annuity(amount, rate, months);
        double total = payment * months;
        return new Quote(bd(amount), months, BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP),
                bd(payment), bd(total), bd(total - amount));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    public record Quote(BigDecimal amount, int termMonths, BigDecimal ratePct, BigDecimal monthlyPayment,
                        BigDecimal totalPayment, BigDecimal overpayment) {
    }
}
