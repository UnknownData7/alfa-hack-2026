package ru.hackathon.credit.company;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Финансовый снимок «по данным банка» о действующем клиенте (синтетический). */
@Entity
@Table(name = "company_financials")
public class CompanyFinancials {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "annual_turnover_12m")
    private BigDecimal annualTurnover12m;

    @Column(name = "avg_monthly_turnover")
    private BigDecimal avgMonthlyTurnover;

    @Column(name = "turnover_growth_pct")
    private BigDecimal turnoverGrowthPct;

    @Column(name = "existing_monthly_obligations")
    private BigDecimal existingMonthlyObligations;

    @Column(name = "as_of")
    private LocalDate asOf;

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public BigDecimal getAnnualTurnover12m() {
        return annualTurnover12m;
    }

    public void setAnnualTurnover12m(BigDecimal annualTurnover12m) {
        this.annualTurnover12m = annualTurnover12m;
    }

    public BigDecimal getAvgMonthlyTurnover() {
        return avgMonthlyTurnover;
    }

    public void setAvgMonthlyTurnover(BigDecimal avgMonthlyTurnover) {
        this.avgMonthlyTurnover = avgMonthlyTurnover;
    }

    public BigDecimal getTurnoverGrowthPct() {
        return turnoverGrowthPct;
    }

    public void setTurnoverGrowthPct(BigDecimal turnoverGrowthPct) {
        this.turnoverGrowthPct = turnoverGrowthPct;
    }

    public BigDecimal getExistingMonthlyObligations() {
        return existingMonthlyObligations;
    }

    public void setExistingMonthlyObligations(BigDecimal existingMonthlyObligations) {
        this.existingMonthlyObligations = existingMonthlyObligations;
    }

    public LocalDate getAsOf() {
        return asOf;
    }

    public void setAsOf(LocalDate asOf) {
        this.asOf = asOf;
    }
}
