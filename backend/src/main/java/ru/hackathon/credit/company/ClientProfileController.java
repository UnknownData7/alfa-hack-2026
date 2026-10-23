package ru.hackathon.credit.company;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.auth.CurrentUser;

@RestController
@RequestMapping("/api/client")
@Tag(name = "Клиент")
public class ClientProfileController {

    private final CompanyService companies;

    public ClientProfileController(CompanyService companies) {
        this.companies = companies;
    }

    public record CompanyProfileDto(Long id, String inn, String name, String legalForm, String legalFormLabel,
                                    String ogrn, String okvedCode, String okvedName, String industryRisk,
                                    LocalDate registrationDate, int businessAgeMonths, int employeesCount,
                                    String taxSystem, String accountMasked, int monthsWithBank,
                                    BigDecimal annualTurnover, BigDecimal avgMonthlyTurnover,
                                    BigDecimal turnoverGrowthPct, BigDecimal existingMonthlyObligations,
                                    LocalDate financialsAsOf) {
    }

    @GetMapping("/profile")
    @Operation(summary = "Профиль компании и финансовый снимок по данным банка")
    public CompanyProfileDto profile() {
        Long companyId = CurrentUser.get().requireCompanyId();
        return toDto(companies.get(companyId), companies.financials(companyId));
    }

    public static CompanyProfileDto toDto(Company c, CompanyFinancials f) {
        return new CompanyProfileDto(c.getId(), c.getInn(), c.getName(), c.getLegalForm(),
                "IP".equals(c.getLegalForm()) ? "Индивидуальный предприниматель" : "Общество с ограниченной ответственностью",
                c.getOgrn(), c.getOkvedCode(), c.getOkvedName(), c.getIndustryRisk(), c.getRegistrationDate(),
                c.businessAgeMonths(LocalDate.now()), c.getEmployeesCount(), c.getTaxSystem(), c.getAccountMasked(),
                c.getMonthsWithBank(), f.getAnnualTurnover12m(), f.getAvgMonthlyTurnover(), f.getTurnoverGrowthPct(),
                f.getExistingMonthlyObligations(), f.getAsOf());
    }
}
