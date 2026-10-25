package ru.hackathon.credit.product;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.auth.CurrentUser;
import ru.hackathon.credit.company.CompanyService;
import ru.hackathon.credit.pricing.PricingCalculator;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Продукты")
public class ProductController {

    private final ProductService products;
    private final CompanyService companies;
    private final PricingCalculator pricing;

    public ProductController(ProductService products, CompanyService companies, PricingCalculator pricing) {
        this.products = products;
        this.companies = companies;
        this.pricing = pricing;
    }

    public record PurposeDto(String code, String label) {
    }

    public record ProductDto(String code, String name, String description, List<PurposeDto> purposes,
                             BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths, int maxTermMonths,
                             boolean requiresCollateral, boolean alwaysManual, BigDecimal minAvgMonthlyTurnover,
                             double indicativeRateFromPct) {
    }

    public record RecommendRequest(@NotNull(message = "Укажите цель") Purpose purpose,
                                   @NotNull(message = "Укажите сумму") @Positive(message = "Сумма должна быть больше нуля") BigDecimal amount,
                                   @NotNull(message = "Укажите срок") @Min(value = 1, message = "Срок от 1 месяца")
                                   @Max(value = 120, message = "Срок не больше 120 месяцев") Integer termMonths) {
    }

    @GetMapping
    @Operation(summary = "Каталог продуктов")
    public List<ProductDto> list() {
        return products.all().stream().map(this::toDto).toList();
    }

    @GetMapping("/purposes")
    @Operation(summary = "Справочник целей финансирования")
    public List<PurposeDto> purposes() {
        return Arrays.stream(Purpose.values()).map(p -> new PurposeDto(p.name(), p.label())).toList();
    }

    @PostMapping("/recommend")
    @Operation(summary = "Подбор продуктов под потребность с объяснением")
    public List<ProductMatcher.Recommendation> recommend(@Valid @RequestBody RecommendRequest request) {
        Long companyId = CurrentUser.get().requireCompanyId();
        double avgTurnover = companies.financials(companyId).getAvgMonthlyTurnover().doubleValue();
        return products.recommend(request.purpose(), request.amount().doubleValue(), request.termMonths(), avgTurnover);
    }

    private ProductDto toDto(Product p) {
        List<PurposeDto> purposes = p.purposeSet().stream()
                .sorted()
                .map(x -> new PurposeDto(x.name(), x.label()))
                .toList();
        double rateFrom = Math.round((pricing.keyRatePct() + p.getSpreadPct().doubleValue() + 1.0) * 100.0) / 100.0;
        return new ProductDto(p.getCode(), p.getName(), p.getDescription(), purposes, p.getMinAmount(),
                p.getMaxAmount(), p.getMinTermMonths(), p.getMaxTermMonths(), p.isRequiresCollateral(),
                p.alwaysManual(), p.getMinAvgMonthlyTurnover(), rateFrom);
    }
}
