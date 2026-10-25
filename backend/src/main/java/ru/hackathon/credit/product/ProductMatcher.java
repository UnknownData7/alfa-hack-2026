package ru.hackathon.credit.product;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Подбор продукта под потребность клиента с объяснением, почему продукт подходит или нет.
 */
public class ProductMatcher {

    private static final Locale RU = Locale.forLanguageTag("ru");

    public record ProductTerms(String code, String name, Set<Purpose> purposes, double minAmount, double maxAmount,
                               int minTerm, int maxTerm, double spreadPct, boolean requiresCollateral,
                               Double minAvgMonthlyTurnover, Double maxTurnoverShare) {
    }

    public record Need(Purpose purpose, double amount, int termMonths) {
    }

    public record Recommendation(String code, String name, boolean available, int relevance,
                                 List<String> fits, List<String> limits, double indicativeRatePct) {
    }

    private final double keyRatePct;

    public ProductMatcher(double keyRatePct) {
        this.keyRatePct = keyRatePct;
    }

    public List<Recommendation> recommend(List<ProductTerms> products, Need need, double avgMonthlyTurnover) {
        List<Recommendation> result = new ArrayList<>();
        for (ProductTerms p : products) {
            List<String> fits = new ArrayList<>();
            List<String> limits = new ArrayList<>();
            int relevance = 0;

            if (p.purposes().contains(need.purpose())) {
                fits.add("подходит для цели «" + need.purpose().label().toLowerCase(RU) + "»");
                relevance += 3;
            } else {
                limits.add("продукт не предназначен для цели «" + need.purpose().label().toLowerCase(RU) + "»");
            }

            if (need.amount() >= p.minAmount() && need.amount() <= p.maxAmount()) {
                fits.add("сумма в пределах продукта");
                relevance += 2;
            } else {
                limits.add(String.format(RU, "сумма должна быть от %,.0f до %,.0f ₽", p.minAmount(), p.maxAmount()));
            }

            if (need.termMonths() >= p.minTerm() && need.termMonths() <= p.maxTerm()) {
                fits.add("срок в пределах продукта");
                relevance += 1;
            } else {
                limits.add("срок должен быть от " + p.minTerm() + " до " + p.maxTerm() + " мес.");
            }

            if (p.minAvgMonthlyTurnover() != null && avgMonthlyTurnover < p.minAvgMonthlyTurnover()) {
                limits.add(String.format(RU, "нужен средний месячный оборот от %,.0f ₽, у вас %,.0f ₽",
                        p.minAvgMonthlyTurnover(), avgMonthlyTurnover));
            } else if (p.minAvgMonthlyTurnover() != null) {
                fits.add("ваш оборот соответствует условию продукта");
            }

            if (p.maxTurnoverShare() != null) {
                double limit = maxAmountByTurnover(p.maxTurnoverShare(), avgMonthlyTurnover);
                if (need.amount() > limit) {
                    limits.add(String.format(RU, "лимит не больше %.0f%% среднего месячного оборота, для вас до %,.0f ₽",
                            p.maxTurnoverShare() * 100, limit));
                } else {
                    fits.add(String.format(RU, "лимит в пределах %.0f%% вашего месячного оборота",
                            p.maxTurnoverShare() * 100));
                }
            }

            if (p.requiresCollateral()) {
                fits.add("нужен залог, решение принимает специалист банка");
            }

            double rate = Math.round((keyRatePct + p.spreadPct() + 1.0) * 100.0) / 100.0;
            result.add(new Recommendation(p.code(), p.name(), limits.isEmpty(), relevance, fits, limits, rate));
        }
        result.sort(Comparator.comparing(Recommendation::available).reversed()
                .thenComparing(Comparator.comparingInt(Recommendation::relevance).reversed()));
        return result;
    }

    /** Максимальная сумма по обороту, округлённая вниз до тысячи рублей. */
    public static double maxAmountByTurnover(double share, double avgMonthlyTurnover) {
        return Math.floor(share * avgMonthlyTurnover / 1000.0) * 1000.0;
    }
}
