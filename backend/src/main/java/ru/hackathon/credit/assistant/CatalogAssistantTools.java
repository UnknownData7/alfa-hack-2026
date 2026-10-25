package ru.hackathon.credit.assistant;

import java.util.List;

import org.springframework.stereotype.Component;

import ru.hackathon.credit.auth.CurrentUser;
import ru.hackathon.credit.company.CompanyService;
import ru.hackathon.credit.product.ProductMatcher;
import ru.hackathon.credit.product.ProductService;
import ru.hackathon.credit.product.Purpose;

@Component
public class CatalogAssistantTools implements AssistantTools {

    private final ProductService products;
    private final CompanyService companies;
    private final ProductFacts facts;

    public CatalogAssistantTools(ProductService products, CompanyService companies, ProductFacts facts) {
        this.products = products;
        this.companies = companies;
        this.facts = facts;
    }

    @Override
    public List<ProductMatcher.Recommendation> recommend(Purpose purpose, double amount, int termMonths) {
        Long companyId = CurrentUser.get().requireCompanyId();
        double avgTurnover = companies.financials(companyId).getAvgMonthlyTurnover().doubleValue();
        return products.recommend(purpose, amount, termMonths, avgTurnover);
    }

    @Override
    public List<ProductFacts.ProductInfo> products() {
        return facts.all();
    }
}
