package ru.hackathon.credit.assistant;

import java.util.List;
import java.util.Set;

import ru.hackathon.credit.product.ProductMatcher;
import ru.hackathon.credit.product.Purpose;

/** Каталог как в демо-данных при ключевой ставке 14%, подбор настоящим {@link ProductMatcher}. */
class FakeTools implements AssistantTools {

    static final List<ProductMatcher.ProductTerms> TERMS = List.of(
            new ProductMatcher.ProductTerms("WORKING_EXPRESS", "Оборотный экспресс",
                    Set.of(Purpose.WORKING_CAPITAL, Purpose.CASH_GAP, Purpose.PURCHASE), 100_000, 3_000_000, 3, 12, 3.0,
                    false, null, null),
            new ProductMatcher.ProductTerms("BUSINESS_OVERDRAFT", "Бизнес-овердрафт",
                    Set.of(Purpose.CASH_GAP, Purpose.WORKING_CAPITAL), 100_000, 2_000_000, 1, 12, 9.0, false, 300_000.0,
                    0.5),
            new ProductMatcher.ProductTerms("EQUIPMENT_GROWTH", "Оборудование и развитие",
                    Set.of(Purpose.EQUIPMENT, Purpose.TRANSPORT, Purpose.NEW_DIRECTION), 500_000, 15_000_000, 12, 84, 4.0,
                    true, null, null));

    private final ProductMatcher matcher = new ProductMatcher(14.0);
    private final double turnover;

    FakeTools(double avgMonthlyTurnover) {
        this.turnover = avgMonthlyTurnover;
    }

    @Override
    public List<ProductMatcher.Recommendation> recommend(Purpose purpose, double amount, int termMonths) {
        return matcher.recommend(TERMS, new ProductMatcher.Need(purpose, amount, termMonths), turnover);
    }

    @Override
    public List<ProductFacts.ProductInfo> products() {
        return TERMS.stream().map(t -> new ProductFacts.ProductInfo(t.code(), t.name(), "Описание " + t.name() + ".",
                t.purposes(), t.minAmount(), t.maxAmount(), t.minTerm(), t.maxTerm(), t.requiresCollateral(),
                14.0 + t.spreadPct() + 1.0)).toList();
    }
}
