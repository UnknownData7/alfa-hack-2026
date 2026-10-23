package ru.hackathon.credit.product;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import ru.hackathon.credit.common.ApiException;

@Service
public class ProductService {

    private final ProductRepository products;
    private final ProductMatcher matcher;

    public ProductService(ProductRepository products, ProductMatcher matcher) {
        this.products = products;
        this.matcher = matcher;
    }

    public List<Product> all() {
        return products.findAllByOrderBySortOrderAsc();
    }

    public Product get(String code) {
        return products.findById(code)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PRODUCT", "Продукт " + code + " не найден"));
    }

    public List<ProductMatcher.Recommendation> recommend(Purpose purpose, double amount, int term, double avgMonthlyTurnover) {
        List<ProductMatcher.ProductTerms> terms = all().stream().map(ProductService::terms).toList();
        return matcher.recommend(terms, new ProductMatcher.Need(purpose, amount, term), avgMonthlyTurnover);
    }

    /** Проверка суммы и срока по границам продукта. */
    public void validateTerms(Product p, BigDecimal amount, int termMonths) {
        if (amount == null || amount.compareTo(p.getMinAmount()) < 0 || amount.compareTo(p.getMaxAmount()) > 0) {
            throw ApiException.unprocessable("AMOUNT_OUT_OF_RANGE", String.format(java.util.Locale.forLanguageTag("ru"),
                    "Сумма для продукта «%s» должна быть от %,.0f до %,.0f ₽", p.getName(),
                    p.getMinAmount(), p.getMaxAmount()));
        }
        if (termMonths < p.getMinTermMonths() || termMonths > p.getMaxTermMonths()) {
            throw ApiException.unprocessable("TERM_OUT_OF_RANGE", "Срок для продукта «" + p.getName() + "» должен быть от "
                    + p.getMinTermMonths() + " до " + p.getMaxTermMonths() + " мес.");
        }
    }

    static ProductMatcher.ProductTerms terms(Product p) {
        return new ProductMatcher.ProductTerms(p.getCode(), p.getName(), p.purposeSet(),
                p.getMinAmount().doubleValue(), p.getMaxAmount().doubleValue(), p.getMinTermMonths(),
                p.getMaxTermMonths(), p.getSpreadPct().doubleValue(), p.isRequiresCollateral(),
                p.getMinAvgMonthlyTurnover() == null ? null : p.getMinAvgMonthlyTurnover().doubleValue());
    }
}
