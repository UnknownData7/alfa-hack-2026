package ru.hackathon.credit.company;

import org.springframework.stereotype.Service;

import ru.hackathon.credit.common.ApiException;

@Service
public class CompanyService {

    private final CompanyRepository companies;
    private final CompanyFinancialsRepository financials;

    public CompanyService(CompanyRepository companies, CompanyFinancialsRepository financials) {
        this.companies = companies;
        this.financials = financials;
    }

    public Company get(Long id) {
        return companies.findById(id).orElseThrow(() -> ApiException.notFound("Компания не найдена"));
    }

    public CompanyFinancials financials(Long companyId) {
        return financials.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Финансовые данные компании не найдены"));
    }
}
