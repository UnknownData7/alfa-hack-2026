package ru.hackathon.credit.assistant;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import ru.hackathon.credit.pricing.PricingCalculator;
import ru.hackathon.credit.product.Product;
import ru.hackathon.credit.product.ProductService;
import ru.hackathon.credit.product.Purpose;

/** Условия продуктов из каталога в виде, удобном помощнику. Новых правил нет, только данные каталога. */
@Component
public class ProductFacts {

    private static final Locale RU = Locale.forLanguageTag("ru");

    /**
     * Продукты с возобновляемым лимитом: долг гасится поступлениями на счёт, а не аннуитетом, поэтому помощник
     * не показывает для них ежемесячный платёж.
     */
    static final Set<String> REVOLVING = Set.of("BUSINESS_OVERDRAFT");

    public record ProductInfo(String code, String name, String description, Set<Purpose> purposes, double minAmount,
                              double maxAmount, int minTerm, int maxTerm, boolean requiresCollateral,
                              double rateFromPct) {

        public boolean revolving() {
            return REVOLVING.contains(code);
        }

        /** Карточка продукта для базы знаний. */
        public String summary() {
            return description + String.format(RU, " Сумма от %,.0f до %,.0f ₽, срок от %d до %d мес., ставка от %s%% годовых.",
                    minAmount, maxAmount, minTerm, maxTerm, AssistantFormat.pct(rateFromPct));
        }
    }

    private final ProductService products;
    private final PricingCalculator pricing;

    public ProductFacts(ProductService products, PricingCalculator pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    public List<ProductInfo> all() {
        return products.all().stream().map(this::info).toList();
    }

    public Optional<ProductInfo> get(String code) {
        return all().stream().filter(p -> p.code().equals(code)).findFirst();
    }

    private ProductInfo info(Product p) {
        // Та же ставка «от», что в каталоге и подборе: ключевая + спред + минимальная премия
        double rateFrom = Math.round((pricing.keyRatePct() + p.getSpreadPct().doubleValue() + 1.0) * 100.0) / 100.0;
        return new ProductInfo(p.getCode(), p.getName(), p.getDescription(), p.purposeSet(),
                p.getMinAmount().doubleValue(), p.getMaxAmount().doubleValue(), p.getMinTermMonths(),
                p.getMaxTermMonths(), p.isRequiresCollateral(), rateFrom);
    }
}
