package ru.hackathon.credit.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ProductMatcherTest {

    private final ProductMatcher matcher = new ProductMatcher(15.0);

    private final List<ProductMatcher.ProductTerms> products = List.of(
            new ProductMatcher.ProductTerms("WORKING_EXPRESS", "Оборотный экспресс",
                    Set.of(Purpose.WORKING_CAPITAL, Purpose.CASH_GAP, Purpose.PURCHASE), 100_000, 3_000_000, 3, 12,
                    3.0, false, null),
            new ProductMatcher.ProductTerms("BUSINESS_OVERDRAFT", "Бизнес-овердрафт",
                    Set.of(Purpose.CASH_GAP, Purpose.WORKING_CAPITAL), 100_000, 2_000_000, 1, 12, 2.5, false, 300_000.0),
            new ProductMatcher.ProductTerms("EQUIPMENT_GROWTH", "Оборудование и развитие",
                    Set.of(Purpose.EQUIPMENT, Purpose.TRANSPORT, Purpose.NEW_DIRECTION), 500_000, 15_000_000, 12, 60,
                    4.0, true, null));

    @Test
    void workingCapitalNeedRecommendsExpressFirstWithExplanation() {
        List<ProductMatcher.Recommendation> r = matcher.recommend(products,
                new ProductMatcher.Need(Purpose.WORKING_CAPITAL, 1_200_000, 9), 4_000_000);
        assertEquals("WORKING_EXPRESS", r.get(0).code());
        assertTrue(r.get(0).available());
        assertFalse(r.get(0).fits().isEmpty());
        assertEquals(19.0, r.get(0).indicativeRatePct());
        ProductMatcher.Recommendation equipment = r.stream().filter(x -> x.code().equals("EQUIPMENT_GROWTH")).findFirst().orElseThrow();
        assertFalse(equipment.available());
        assertFalse(equipment.limits().isEmpty());
    }

    @Test
    void overdraftUnavailableBelowTurnoverThreshold() {
        List<ProductMatcher.Recommendation> r = matcher.recommend(products,
                new ProductMatcher.Need(Purpose.CASH_GAP, 500_000, 12), 250_000);
        ProductMatcher.Recommendation overdraft = r.stream().filter(x -> x.code().equals("BUSINESS_OVERDRAFT")).findFirst().orElseThrow();
        assertFalse(overdraft.available());
    }
}
